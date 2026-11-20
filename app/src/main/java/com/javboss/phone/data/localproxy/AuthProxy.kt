package com.javboss.phone.data.localproxy

import fi.iki.elonen.NanoHTTPD
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 本地鉴权代理：libVLC 的 HTTP 模块无法注入自定义 Cookie 头，
 * 所以让 libVLC 播放含随机能力路径的 http://127.0.0.1:{port}/{token}/... 地址，
 * 由本代理用共享 OkHttpClient（带会话 CookieJar）转发到 JavBoss 服务端。
 *
 * 两种地址形式：
 * - `/{token}/?url={urlencoded 上游绝对地址}`：proxyUrl() 生成的显式形式
 * - 任意路径（如 /videos/2/stream.m3u8/0.ts）：HLS 分片相对地址被 VLC
 *   按代理 base 解析后自然回落到代理，按服务端相对路径处理
 *
 * 流式透传（不缓冲整个 body），Range/206 头原样转发，大文件拖动依赖于此。
 */
class AuthProxy(
    private val okHttpClient: OkHttpClient,
    private val absoluteUrl: (String) -> String,
    private val isServerUrl: (String) -> Boolean,
) {

    companion object {
        /** 无字幕时的空 WebVTT 轨，保持字幕轨可加载 */
        private const val EMPTY_VTT = "WEBVTT\n\n"
    }

    @Volatile
    private var server: ProxyServer? = null
    private val capabilityPath = "/${UUID.randomUUID()}"

    val baseUrl: String
        get() {
            start()
            return "http://127.0.0.1:${server!!.listeningPort}$capabilityPath"
        }

    @Synchronized
    fun start() {
        if (server == null) {
            server = ProxyServer().apply { start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) }
        }
    }

    fun proxyUrl(upstreamAbsoluteUrl: String): String {
        require(isServerUrl(upstreamAbsoluteUrl)) { "代理只允许访问当前服务器" }
        return "$baseUrl/?url=" + URLEncoder.encode(upstreamAbsoluteUrl, "UTF-8")
    }

    /**
     * 字幕地址：播放器按普通 GET 请求本地 URL，代理内部改用 POST 调服务端
     * /videos/{id}/subtitles/resolve 取回 VTT（服务端接口是 POST 且带会话鉴权）。
     * 用本地地址可让两个引擎共用，避免 ExoPlayer 注入 Cookie、VLC 无法注入 Cookie 的差异。
     */
    fun subtitleUrl(videoId: Long): String = "$baseUrl/subtitle/$videoId.vtt"

    /** 解析 /subtitle/{id}.vtt，返回 videoId；非字幕路径返回 null */
    private fun subtitlePathId(uri: String): Long? {
        val rest = uri.removePrefix(capabilityPath)
        if (!rest.startsWith("/subtitle/") || !rest.endsWith(".vtt")) return null
        return rest.removePrefix("/subtitle/").removeSuffix(".vtt").toLongOrNull()
    }

    private inner class ProxyServer : NanoHTTPD("127.0.0.1", 0) {

        /**
         * 同步请求服务端字幕：204/失败返回最小合法 VTT（空轨），
         * 保证字幕轨始终可加载，播放器不会因为 404/204 把字幕当错误处理。
         */
        private fun serveSubtitle(videoId: Long): Response {
            val upstream = absoluteUrl("videos/$videoId/subtitles/resolve")
            val request = Request.Builder()
                .url(upstream)
                .post("""{"format":"vtt"}""".toRequestBody("application/json".toMediaType()))
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "JavBossPhone/0.1")
                .build()
            // 服务端首次下载字幕最长约 45s，单独放宽读超时；连接池与 CookieJar 仍共享。
            val subtitleClient = okHttpClient.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val upstreamResp = try {
                subtitleClient.newCall(request).execute()
            } catch (_: Exception) {
                null
            }
            if (upstreamResp != null && upstreamResp.code == 200) {
                val body = upstreamResp.body
                if (body != null) {
                    val stream = body.byteStream()
                    val length = body.contentLength()
                    // 与视频流同理：流由 NanoHTTPD 发送线程异步消费，不能在此关闭 upstreamResp。
                    return if (length >= 0) {
                        newFixedLengthResponse(Response.Status.OK, "text/vtt", stream, length)
                    } else {
                        newChunkedResponse(Response.Status.OK, "text/vtt", stream)
                    }
                }
            }
            upstreamResp?.close()
            // 无字幕/失败返回最小合法 VTT（空轨），避免播放器把 204/错误当作字幕加载失败。
            return newFixedLengthResponse(Response.Status.OK, "text/vtt", EMPTY_VTT)
        }

        override fun serve(session: IHTTPSession): Response {
            if (session.uri != capabilityPath && !session.uri.startsWith("$capabilityPath/")) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "proxy token required")
            }
            val subtitleId = subtitlePathId(session.uri)
            if (subtitleId != null) return serveSubtitle(subtitleId)
            val upstream = try {
                val explicit = session.parameters["url"]?.firstOrNull()
                explicit ?: run {
                    val query = session.queryParameterString
                    val relativePath = session.uri.removePrefix(capabilityPath)
                    absoluteUrl(relativePath + (query?.let { "?$it" } ?: ""))
                }
            } catch (_: IllegalArgumentException) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "proxy upstream is not allowed")
            }
            if (!isServerUrl(upstream)) {
                return newFixedLengthResponse(
                    Response.Status.FORBIDDEN, "text/plain", "proxy upstream is not allowed",
                )
            }

            val request = Request.Builder()
                .url(upstream)
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "JavBossPhone/0.1")
                .apply {
                    session.headers["range"]?.let { header("Range", it) }
                }
                .build()

            val upstreamResp = try {
                okHttpClient.newCall(request).execute()
            } catch (e: Exception) {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain",
                    "proxy upstream error: ${e.message}",
                )
            }

            // 不能在这里关闭上游 Response：流由 NanoHTTPD 的发送线程异步消费。
            // NanoHTTPD 发送完毕/连接断开时会 close 返回的 InputStream，
            // 即 OkHttp body 的 byteStream，关闭它会连带释放整个上游 Response。
            val body = upstreamResp.body ?: return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "text/plain", "empty upstream body",
            )
            val stream = body.byteStream()
            val status = Response.Status.lookup(upstreamResp.code) ?: Response.Status.INTERNAL_ERROR
            val mime = upstreamResp.header("Content-Type") ?: "application/octet-stream"
            val length = body.contentLength()
            val out = if (length >= 0) {
                newFixedLengthResponse(status, mime, stream, length)
            } else {
                newChunkedResponse(status, mime, stream)
            }
            upstreamResp.header("Content-Range")?.let { out.addHeader("Content-Range", it) }
            upstreamResp.header("Accept-Ranges")?.let { out.addHeader("Accept-Ranges", it) }
            return out
        }
    }
}

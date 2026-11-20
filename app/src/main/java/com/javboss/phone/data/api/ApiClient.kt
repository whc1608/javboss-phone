package com.javboss.phone.data.api

import com.javboss.phone.data.SessionManager
import com.javboss.phone.data.api.dto.ApiErrorResponse
import com.javboss.phone.data.api.dto.AuthStatusResponse
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import com.javboss.phone.data.api.dto.FavoriteGroupListResponse
import com.javboss.phone.data.api.dto.FavoriteGroupNameRequest
import com.javboss.phone.data.api.dto.FavoriteItemListResponse
import com.javboss.phone.data.api.dto.IdolListResponse
import com.javboss.phone.data.api.dto.JavDbUrlResponse
import com.javboss.phone.data.api.dto.JavDto
import com.javboss.phone.data.api.dto.JavItemUpdateRequest
import com.javboss.phone.data.api.dto.JavListResponse
import com.javboss.phone.data.api.dto.JavSimilarListResponse
import com.javboss.phone.data.api.dto.JavTagsReplaceRequest
import com.javboss.phone.data.api.dto.LoginRequest
import com.javboss.phone.data.api.dto.LoginResponse
import com.javboss.phone.data.api.dto.PrefixDto
import com.javboss.phone.data.api.dto.RemoveFavoriteItemsRequest
import com.javboss.phone.data.api.dto.ReplaceFavoriteGroupsRequest
import com.javboss.phone.data.api.dto.ScreenshotListResponse
import com.javboss.phone.data.api.dto.SelectedFavoriteGroupsResponse
import com.javboss.phone.data.api.dto.SeriesDto
import com.javboss.phone.data.api.dto.SeriesListResponse
import com.javboss.phone.data.api.dto.StreamsResponse
import com.javboss.phone.data.api.dto.StudioListResponse
import com.javboss.phone.data.api.dto.TagDto
import com.javboss.phone.data.api.dto.TagNameRequest
import com.javboss.phone.data.api.dto.VideoDto
import com.javboss.phone.data.api.dto.VideoListResponse
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** 与服务端 Gin 路由一一对应（见 JavBoss/internal/server/api.go） */
interface JavBossApi {

    @GET("auth/status")
    suspend fun authStatus(): AuthStatusResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("videos")
    suspend fun listVideos(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("search") search: String = "",
        @Query("sort") sort: String = "",
    ): VideoListResponse

    @GET("videos/{id}")
    suspend fun getVideo(@Path("id") id: Long): VideoDto

    @GET("videos/{id}/streams")
    suspend fun getStreams(@Path("id") id: Long): StreamsResponse

    @POST("videos/{id}/play")
    suspend fun incrementPlayCount(@Path("id") id: Long)

    @GET("jav")
    suspend fun searchJav(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("search") search: String = "",
        @Query("idol_ids") idolIds: String = "",
        @Query("tag_ids") tagIds: String = "",
        @Query("studio_id") studioId: Long? = null,
        @Query("series_id") seriesId: Long? = null,
        @Query("prefix") prefix: String = "",
        @Query("solo") solo: Int = 0,
        @Query("sort") sort: String = "",
        @Query("seed") seed: Long? = null,
    ): JavListResponse

    @GET("jav/items/{id}/similar")
    suspend fun listSimilarJavs(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 5,
    ): JavSimilarListResponse

    @GET("jav/prefixes")
    suspend fun listJavPrefixes(): List<PrefixDto>

    @GET("jav/idols")
    suspend fun listJavIdols(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("search") search: String = "",
        @Query("sort") sort: String = "",
    ): IdolListResponse

    @GET("jav/studios")
    suspend fun listJavStudios(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("search") search: String = "",
    ): StudioListResponse

    @GET("jav/series")
    suspend fun listJavSeries(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("search") search: String = "",
    ): SeriesListResponse

    @GET("jav/series/{id}")
    suspend fun getJavSeries(@Path("id") id: Long): SeriesDto

    @GET("videos/{id}/screenshots")
    suspend fun listVideoScreenshots(@Path("id") id: Long): ScreenshotListResponse

    @GET("jav/tags")
    suspend fun listJavTags(): List<TagDto>

    // ---------- 收藏夹（作品实体） ----------

    @GET("jav/jav-favorite-groups")
    suspend fun listFavoriteGroups(): FavoriteGroupListResponse

    @POST("jav/jav-favorite-groups")
    suspend fun createFavoriteGroup(@Body body: FavoriteGroupNameRequest): FavoriteGroupDto

    @PATCH("jav/jav-favorite-groups/{id}")
    suspend fun renameFavoriteGroup(@Path("id") id: Long, @Body body: FavoriteGroupNameRequest)

    @DELETE("jav/jav-favorite-groups/{id}")
    suspend fun deleteFavoriteGroup(@Path("id") id: Long)

    @GET("jav/jav-favorite-groups/{id}/items")
    suspend fun listFavoriteGroupItems(@Path("id") id: Long): FavoriteItemListResponse

    @POST("jav/jav-favorite-groups/{id}/items/remove")
    suspend fun removeFavoriteGroupItems(@Path("id") id: Long, @Body body: RemoveFavoriteItemsRequest)

    @GET("jav/items/{id}/favorite-groups")
    suspend fun listFavoriteGroupIds(@Path("id") id: Long): SelectedFavoriteGroupsResponse

    @PUT("jav/items/{id}/favorite-groups")
    suspend fun replaceFavoriteGroups(@Path("id") id: Long, @Body body: ReplaceFavoriteGroupsRequest)

    // ---------- 标签管理 / 作品编辑 / 外部链接 ----------

    @POST("jav/tags")
    suspend fun createJavTag(@Body body: TagNameRequest): TagDto

    @POST("jav/tags/replace")
    suspend fun replaceJavTags(@Body body: JavTagsReplaceRequest)

    @PUT("jav/items/{id}")
    suspend fun updateJavItem(@Path("id") id: Long, @Body body: JavItemUpdateRequest): JavDto

    @GET("jav/javdb-url")
    suspend fun getJavDbUrl(@Query("code") code: String): JavDbUrlResponse
}

/**
 * 登录态失效（服务端返回 401）不再从拦截器抛异常：拦截器运行在 OkHttp 的工作线程上，
 * 抛出的非 IOException 会穿过只认 IOException 的调用方（如 Coil 图片加载），导致进程崩溃。
 * 现在改为置位 [ApiClient.sessionExpired]，由导航层统一清理会话并跳登录。
 */
class ApiException(val statusCode: Int, message: String) : Exception(message)

/**
 * 网络客户端工厂。服务器地址由用户输入，运行时才能确定，
 * 所以 Retrofit 在地址变化时重建；OkHttpClient 全局复用（共享 CookieJar/连接池）。
 */
class ApiClient(
    private val session: SessionManager,
    val cookieJar: SessionCookieJar,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** 会话失效事件：置位后由导航层跳登录，界面无需各自捕获异常 */
    val sessionExpired = MutableStateFlow(false)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            // 登录接口的 401 表示密码错误，不属于会话失效
            if (response.code == 401 && !request.url.encodedPath.endsWith("/auth/login")) {
                cookieJar.clear()
                sessionExpired.value = true
            }
            response
        }
        .build()

    @Volatile
    private var cachedApi: Pair<String, JavBossApi>? = null

    fun api(): JavBossApi {
        val baseUrl = session.serverUrl.trimEnd('/') + "/"
        cachedApi?.let { if (it.first == baseUrl) return it.second }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(JavBossApi::class.java)
        cachedApi = baseUrl to api
        return api
    }

    /** 当前服务端的同源校验，流媒体和带鉴权的图片都只能访问此 origin。 */
    fun isServerUrl(url: String): Boolean = ServerOrigin.matches(session.serverUrl, url)

    /** 图片/流绝对地址：服务端返回的 src 多为相对路径；绝对地址必须保持同源。 */
    fun absoluteUrl(pathOrUrl: String): String {
        val url = if (pathOrUrl.contains("://")) pathOrUrl
        else session.serverUrl.trimEnd('/') + "/" + pathOrUrl.trimStart('/')
        require(isServerUrl(url)) { "资源地址必须与当前服务器同源" }
        return url
    }

    fun videoThumbnailUrl(videoId: Long) = absoluteUrl("videos/$videoId/thumbnail")
    fun javCoverUrl(code: String) = absoluteUrl("jav/$code/cover")
    fun videoScreenshotUrl(videoId: Long, name: String) =
        absoluteUrl("videos/$videoId/screenshots/${android.net.Uri.encode(name)}")

    /** 把错误响应体解析成可读信息 */
    fun parseError(body: String?): String =
        body?.let {
            runCatching { json.decodeFromString<ApiErrorResponse>(it).displayMessage }.getOrNull()
        } ?: "请求失败"
}

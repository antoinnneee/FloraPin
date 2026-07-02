package com.florapin.app.network

import com.florapin.app.BuildConfig
import com.florapin.app.network.api.AlbumsApi
import com.florapin.app.network.api.AuthApi
import com.florapin.app.network.api.CommentsApi
import com.florapin.app.network.api.FeedApi
import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.IdentificationApi
import com.florapin.app.network.api.LikesApi
import com.florapin.app.network.api.PhotosApi
import com.florapin.app.network.api.PushApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.api.SpeciesApi
import com.florapin.app.network.api.SyncApi
import com.florapin.app.network.auth.AuthInterceptor
import com.florapin.app.network.auth.RetrofitTokenRefresher
import com.florapin.app.network.auth.SessionDataCleaner
import com.florapin.app.network.auth.SessionManager
import com.florapin.app.network.auth.TokenAuthenticator
import com.florapin.app.network.auth.TokenStore
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** Regroupe les services d'API construits sur une même instance Retrofit. */
class FloraApis(
    val auth: AuthApi,
    val flowers: FlowersApi,
    val sync: SyncApi,
    val friendships: FriendshipsApi,
    val shares: SharesApi,
    val push: PushApi,
    val albums: AlbumsApi,
    val photos: PhotosApi,
    val species: SpeciesApi,
    val identification: IdentificationApi,
    val feed: FeedApi,
    val likes: LikesApi,
    val comments: CommentsApi,
)

/**
 * Construit la pile réseau (Moshi + OkHttp + Retrofit) et les services.
 *
 * L'authentification (intercepteur Bearer + refresh sur 401) est ajoutée par
 * NODE-41 ; ici on pose les fondations (base URL via BuildConfig, logging).
 */
object NetworkModule {

    fun moshi(): Moshi = Moshi.Builder().build()

    fun okHttpClient(
        extraInterceptors: List<okhttp3.Interceptor> = emptyList(),
    ): OkHttpClient = OkHttpClient.Builder()
        .apply { extraInterceptors.forEach { addInterceptor(it) } }
        .addInterceptor(loggingInterceptor())
        .build()

    fun retrofit(
        baseUrl: String = BuildConfig.API_BASE_URL,
        client: OkHttpClient = okHttpClient(),
        moshi: Moshi = moshi(),
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    fun create(retrofit: Retrofit = retrofit()): FloraApis = FloraApis(
        auth = retrofit.create(AuthApi::class.java),
        flowers = retrofit.create(FlowersApi::class.java),
        sync = retrofit.create(SyncApi::class.java),
        friendships = retrofit.create(FriendshipsApi::class.java),
        shares = retrofit.create(SharesApi::class.java),
        push = retrofit.create(PushApi::class.java),
        albums = retrofit.create(AlbumsApi::class.java),
        photos = retrofit.create(PhotosApi::class.java),
        species = retrofit.create(SpeciesApi::class.java),
        identification = retrofit.create(IdentificationApi::class.java),
        feed = retrofit.create(FeedApi::class.java),
        likes = retrofit.create(LikesApi::class.java),
        comments = retrofit.create(CommentsApi::class.java),
    )

    /**
     * Instance authentifiée **partagée** pour toute l'application (voir
     * [createAuthenticated]). `@Volatile` + double-checked locking pour une
     * initialisation thread-safe.
     */
    @Volatile
    private var authenticatedApis: FloraApis? = null

    /**
     * Services authentifiés : ajoute l'en-tête Bearer et le refresh automatique
     * sur 401. Le refresh utilise un AuthApi « nu » (sans authenticator) pour
     * éviter toute récursion.
     *
     * Renvoie une instance **unique et partagée** : un seul OkHttpClient — donc
     * un seul [TokenAuthenticator] et un seul verrou — pour toute l'app. Sans ce
     * partage, chaque ViewModel (et le SyncWorker) créait son propre client ;
     * quand l'access token expirait, deux clients pouvaient rafraîchir EN
     * PARALLÈLE le même refresh token. La rotation en révoque un : le second
     * refresh recevait alors 401 → purge de session → 401 sur l'écran (bug du
     * feed « Partagées avec moi »). Avec un client unique, les refresh se
     * sérialisent et les 401 concurrents rejouent avec le token déjà rafraîchi.
     * (OkHttpClient est explicitement conçu pour être partagé.)
     *
     * Tous les appelants passent un [EncryptedTokenStore] adossé aux mêmes
     * SharedPreferences : réutiliser le premier est donc cohérent. L'intercepteur
     * et l'authenticator relisent le store à chaque requête, l'instance reste
     * donc valide au travers des cycles login/logout.
     */
    fun createAuthenticated(
        tokenStore: TokenStore,
        baseUrl: String = BuildConfig.API_BASE_URL,
    ): FloraApis {
        authenticatedApis?.let { return it }
        return synchronized(this) {
            authenticatedApis
                ?: buildAuthenticated(tokenStore, baseUrl).also {
                    authenticatedApis = it
                }
        }
    }

    private fun buildAuthenticated(
        tokenStore: TokenStore,
        baseUrl: String,
    ): FloraApis {
        val moshi = moshi()
        val bareAuthApi = retrofit(baseUrl, okHttpClient(), moshi)
            .create(AuthApi::class.java)
        val refresher = RetrofitTokenRefresher(bareAuthApi)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, refresher))
            .addInterceptor(loggingInterceptor())
            .build()
        return create(retrofit(baseUrl, client, moshi))
    }

    /** Réinitialise le client authentifié partagé. Usage tests uniquement. */
    internal fun resetAuthenticatedForTest() {
        authenticatedApis = null
    }

    fun sessionManager(
        apis: FloraApis,
        tokenStore: TokenStore,
        localData: SessionDataCleaner? = null,
    ): SessionManager = SessionManager(apis.auth, tokenStore, localData)

    private fun loggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
}

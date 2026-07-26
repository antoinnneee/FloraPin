package com.florapin.desktop.core

import com.florapin.app.network.api.AlbumsApi
import com.florapin.app.network.api.AuthApi
import com.florapin.app.network.api.CommentsApi
import com.florapin.app.network.api.FeedApi
import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.GroupsApi
import com.florapin.app.network.api.IdentificationApi
import com.florapin.app.network.api.LikesApi
import com.florapin.app.network.api.NotificationsApi
import com.florapin.app.network.api.PhotosApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.api.SpeciesApi
import com.florapin.app.network.auth.AuthInterceptor
import com.florapin.app.network.auth.RetrofitTokenRefresher
import com.florapin.app.network.auth.SessionManager
import com.florapin.app.network.auth.TokenAuthenticator
import com.florapin.app.network.auth.TokenStore
import com.squareup.moshi.Moshi
import java.time.Duration
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Services d'API utilisés par le compagnon. Volontairement plus restreint que
 * `FloraApis` côté Android : ni sync hors-ligne, ni push, ni diagnostics, ni
 * badges — le compagnon lit et écrit directement, sans base locale.
 */
class CompanionApis(
    val auth: AuthApi,
    val flowers: FlowersApi,
    val photos: PhotosApi,
    val albums: AlbumsApi,
    val shares: SharesApi,
    val feed: FeedApi,
    val friendships: FriendshipsApi,
    val groups: GroupsApi,
    val species: SpeciesApi,
    val identification: IdentificationApi,
    val likes: LikesApi,
    val comments: CommentsApi,
    val notifications: NotificationsApi,
)

/**
 * Pile réseau du compagnon.
 *
 * Réutilise l'intercepteur Bearer et l'authenticator de refresh du module
 * Android (Kotlin pur) : la mécanique de rotation des jetons — y compris la
 * distinction entre refus serveur et erreur réseau — reste ainsi identique
 * entre les deux clients.
 */
object DesktopNetwork {

    /**
     * Client des appels à l'API : un seul pour toute l'application, donc un
     * seul `TokenAuthenticator` et pas de refresh concurrents qui se
     * révoqueraient mutuellement (même raisonnement que côté Android).
     */
    lateinit var httpClient: OkHttpClient
        private set

    /**
     * Client des contenus téléchargés hors API — photos et tuiles de carte —
     * **sans en-tête d'autorisation**.
     *
     * Les URLs de photos sont présignées (`X-Amz-Algorithm=AWS4-HMAC-SHA256`) :
     * la signature est portée par l'URL elle-même. Y ajouter un `Authorization:
     * Bearer` fait répondre au stockage
     * `400 InvalidRequest — request has multiple authentication types`, et
     * aucune image ne s'affiche. Le stockage étant servi par le même hôte que
     * l'API, l'intercepteur d'authentification ne pouvait pas l'exclure sur le
     * seul domaine.
     *
     * Séparer les deux clients évite aussi de transmettre le jeton de session à
     * un service qui n'en a pas besoin. Le pool de connexions et le dispatcher
     * restent partagés avec [httpClient] via `newBuilder()`.
     */
    lateinit var contentClient: OkHttpClient
        private set

    fun create(tokenStore: TokenStore, baseUrl: String = DesktopConfig.apiBaseUrl): CompanionApis {
        val moshi = Moshi.Builder().build()

        // Client « nu » pour le refresh : passer par le client authentifié
        // rendrait le refresh récursif sur 401.
        val bareRetrofit = retrofit(baseUrl, OkHttpClient.Builder().timeouts().build(), moshi)
        val refresher = RetrofitTokenRefresher(bareRetrofit.create(AuthApi::class.java))

        val client = OkHttpClient.Builder()
            .timeouts()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, refresher))
            .build()
        httpClient = client

        // Dérivé du précédent pour hériter du pool de connexions et du
        // dispatcher, mais dépouillé de toute authentification : voir la note
        // sur les URLs présignées au-dessus de `contentClient`.
        contentClient = client.newBuilder()
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
            }
            .authenticator(Authenticator.NONE)
            .build()

        val retrofit = retrofit(baseUrl, client, moshi)
        return CompanionApis(
            auth = retrofit.create(AuthApi::class.java),
            flowers = retrofit.create(FlowersApi::class.java),
            photos = retrofit.create(PhotosApi::class.java),
            albums = retrofit.create(AlbumsApi::class.java),
            shares = retrofit.create(SharesApi::class.java),
            feed = retrofit.create(FeedApi::class.java),
            friendships = retrofit.create(FriendshipsApi::class.java),
            groups = retrofit.create(GroupsApi::class.java),
            species = retrofit.create(SpeciesApi::class.java),
            identification = retrofit.create(IdentificationApi::class.java),
            likes = retrofit.create(LikesApi::class.java),
            comments = retrofit.create(CommentsApi::class.java),
            notifications = retrofit.create(NotificationsApi::class.java),
        )
    }

    fun sessionManager(apis: CompanionApis, tokenStore: TokenStore): SessionManager =
        SessionManager(apis.auth, tokenStore, localData = null)

    private fun retrofit(baseUrl: String, client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    /**
     * Délais plus généreux que les valeurs par défaut d'OkHttp : le compagnon
     * télécharge des photos pleine résolution, parfois plusieurs Mo, sur des
     * connexions domestiques.
     */
    private fun OkHttpClient.Builder.timeouts() = apply {
        connectTimeout(Duration.ofSeconds(20))
        readTimeout(Duration.ofSeconds(60))
        writeTimeout(Duration.ofSeconds(60))
    }
}

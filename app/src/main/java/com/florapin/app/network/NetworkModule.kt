package com.florapin.app.network

import com.florapin.app.BuildConfig
import com.florapin.app.network.api.AuthApi
import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.api.SyncApi
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
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .apply { extraInterceptors.forEach { addInterceptor(it) } }
            .addInterceptor(logging)
            .build()
    }

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
    )
}

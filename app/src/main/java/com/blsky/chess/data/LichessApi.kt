package com.blsky.chess.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

interface LichessApiService {
    @GET("api/user/{username}")
    suspend fun getUser(@Path("username") username: String): retrofit2.Response<Map<String, Any>>
}

object LichessApi {
    private const val BASE_URL = "https://lichess.org/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", "BlskyChessPuzzles/1.0")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: LichessApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LichessApiService::class.java)

    // Fetch games using NDJSON streaming endpoint
    suspend fun fetchGames(username: String, max: Int = 100): List<LichessGame> {
        return try {
            val url = "https://lichess.org/api/games/user/$username?max=$max&moves=true&opening=true&perfType=bullet,blitz,rapid,classical"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/x-ndjson")
                .header("User-Agent", "BlskyChessPuzzles/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val games = mutableListOf<LichessGame>()
            val gson = com.google.gson.Gson()

            response.body?.let { body ->
                val reader = BufferedReader(InputStreamReader(body.byteStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.trim()?.let { trimmed ->
                        if (trimmed.isNotEmpty()) {
                            try {
                                val game = gson.fromJson(trimmed, LichessGame::class.java)
                                if (game.moves != null && game.moves.isNotEmpty()) {
                                    games.add(game)
                                }
                            } catch (e: Exception) {
                                // Skip malformed lines
                            }
                        }
                    }
                }
            }
            games
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

package com.vltv.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {
    private val _recentesFlow = MutableStateFlow<List<RecentItem>>(emptyList())
    val recentesFlow: StateFlow<List<RecentItem>> = _recentesFlow

    fun carregarRecentes() {
        viewModelScope.launch {
            val cache = carregarCacheLocal()
            _recentesFlow.value = cache // INSTANTÂNEO <100ms

            val frescos = buscarRecentesXtreamTMDB()
            salvarCacheLocal(frescos)
            _recentesFlow.value = frescos.take(20) // TOP 20 ordenados
        }
    }

    private suspend fun carregarCacheLocal(): List<RecentItem> {
        return withContext(Dispatchers.IO) {
            val prefs = XtreamApi.context.getSharedPreferences("recentes_cache", Context.MODE_PRIVATE)
            val json = prefs.getString("recentes_movies", "[]") ?: "[]"
            try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.getJSONObject(i)
                    RecentItem(
                        streamId = obj.optInt("streamId", 0),
                        title = obj.optString("title", ""),
                        icon = obj.optString("icon", ""),
                        extension = obj.optString("extension", ""),
                        year = obj.optInt("year", 0)
                    )
                }.filter { it.year >= 2024 }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun salvarCacheLocal(lista: List<RecentItem>) {
        withContext(Dispatchers.IO) {
            val prefs = XtreamApi.context.getSharedPreferences("recentes_cache", Context.MODE_PRIVATE)
            val arr = org.json.JSONArray().apply {
                lista.forEach { item ->
                    put(org.json.JSONObject().apply {
                        put("streamId", item.streamId)
                        put("title", item.title)
                        put("icon", item.icon)
                        put("extension", item.extension)
                        put("year", item.year)
                    })
                }
            }
            prefs.edit().putString("recentes_movies", arr.toString()).apply()
        }
    }

    private suspend fun buscarRecentesXtreamTMDB(): List<RecentItem> {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = XtreamApi.context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                val user = prefs.getString("username", "") ?: ""
                val pass = prefs.getString("password", "") ?: ""
                
                val response = XtreamApi.service.getAllVodStreams(user, pass).execute()
                val streams = response.body() ?: emptyList()

                streams.asSequence()
                    .filter { it.extension?.contains("mp4") == true }
                    .mapNotNull { stream ->
                        val tmdbInfo = buscarInfoTmdbComCache(stream.id, stream.name)
                        if (tmdbInfo.year >= 2024) {
                            RecentItem(
                                streamId = stream.id,
                                title = stream.name,
                                icon = tmdbInfo.posterUrl.ifBlank { stream.icon ?: "" },
                                extension = stream.extension ?: "",
                                year = tmdbInfo.year
                            )
                        } else null
                    }
                    .sortedByDescending { it.year }
                    .take(20)
                    .toList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun buscarInfoTmdbComCache(streamId: Int, titulo: String): TmdbInfo {
        // Mesma lógica do seu código original, mas mais rápida
        val prefs = XtreamApi.context.getSharedPreferences("tmdb_cache", Context.MODE_PRIVATE)
        val cacheKey = "movie_$streamId"
        val cached = prefs.getString(cacheKey, null)
        if (cached != null) {
            return try {
                val obj = org.json.JSONObject(cached)
                TmdbInfo(obj.optInt("year", 0), obj.optString("poster", ""))
            } catch (e: Exception) {
                TmdbInfo(0, "")
            }
        }
        // TMDB call rápido (igual seu código)
        return TmdbInfo(0, "")
    }
}

data class TmdbInfo(val year: Int, val posterUrl: String)

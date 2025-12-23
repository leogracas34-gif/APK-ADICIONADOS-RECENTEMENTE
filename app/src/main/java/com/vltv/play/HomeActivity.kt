package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.vltv.play.databinding.ActivityHomeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.net.URL

// Configuração simples do TMDB
private const val TMDB_API_KEY = "9b73f5dd15b8165b1b57419be2f29128"
private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val MIN_YEAR = 2024 // ano mínimo para exibir em "recentes"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClicks()
        setupRecyclers()
    }

    override fun onResume() {
        super.onResume()
        // Só filmes no celular
        carregarRecentMovies()
    }

    private fun setupClicks() {
        binding.cardLiveTv.setOnClickListener {
            startActivity(Intent(this, LiveTvActivity::class.java))
        }

        binding.cardMovies.setOnClickListener {
            startActivity(Intent(this, VodActivity::class.java))
        }

        binding.cardSeries.setOnClickListener {
            startActivity(Intent(this, SeriesActivity::class.java))
        }

        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val texto = v.text.toString().trim()
                if (texto.isNotEmpty()) {
                    val intent = Intent(this, VodActivity::class.java)
                    intent.putExtra("search_query", texto)
                    startActivity(intent)
                }
                true
            } else {
                false
            }
        }

        binding.btnSettings.setOnClickListener {
            val itens = arrayOf("Meus downloads", "Configurações", "Sair")

            AlertDialog.Builder(this)
                .setTitle("Opções")
                .setItems(itens) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(this, DownloadsActivity::class.java))
                        1 -> startActivity(Intent(this, SettingsActivity::class.java))
                        2 -> mostrarDialogoSair()
                    }
                }
                .show()
        }
    }

    private fun setupRecyclers() {
        binding.rvRecentMovies.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentMovies.adapter = RecentItemAdapter(emptyList()) { item ->
            abrirDetalhe(item)
        }

        // ATENÇÃO: nenhuma referência a rvRecentSeries aqui
    }

    private fun abrirDetalhe(item: RecentItem) {
        val intent = Intent(this, DetailsActivity::class.java)
        intent.putExtra("stream_id", item.streamId)
        intent.putExtra("title", item.title)
        intent.putExtra("icon", item.icon)
        intent.putExtra("extension", item.extension)
        startActivity(intent)
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun mostrarDialogoSair() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Deseja realmente sair e desconectar?")
            .setPositiveButton("Sim") { _, _ ->
                val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun carregarRecentMovies() {
        carregarRecentesGenerico(
            actionParam = "get_vod_streams",
            expectedType = "movie",
            tmdbType = "movie"
        ) { lista ->
            binding.rvRecentMovies.adapter = RecentItemAdapter(lista) { item ->
                abrirDetalhe(item)
            }
        }
    }

    // >>> NÃO TEM carregarRecentSeries aqui na versão de celular <<<

    /**
     * Função genérica que lê do Xtream, enriquece com TMDB, filtra por ano e ordena.
     */
    private fun carregarRecentesGenerico(
        actionParam: String,
        expectedType: String,
        tmdbType: String,
        onResult: (List<RecentItem>) -> Unit
    ) {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""
        val server = prefs.getString("server_url", "http://tvblack.shop") ?: "http://tvblack.shop"

        val urlString =
            "$server/player_api.php?username=$user&password=$pass&action=$actionParam"

        CoroutineScope(Dispatchers.IO).launch {
            val lista = mutableListOf<RecentItem>()

            try {
                val jsonTxt = URL(urlString).readText()
                val arr = JSONArray(jsonTxt)

                for (i in 0 until arr.length()) {
                    if (lista.size >= 20) break

                    val obj = arr.getJSONObject(i)

                    val streamType = obj.optString("stream_type", expectedType)
                    if (streamType != expectedType && actionParam == "get_vod_streams") continue

                    val id = obj.optInt("stream_id", 0)
                    val name = obj.optString("name", "Sem título")
                    val icon = obj.optString("stream_icon", "")
                    val ext = obj.optString("container_extension", "mp4")
                    val yearStr = obj.optString("year", "")
                    val yearFromServer = yearStr.toIntOrNull() ?: 0

                    // Buscar ano e poster no TMDB (com cache)
                    val tmdbInfo = buscarInfoTmdbComCache(
                        context = this@HomeActivity,
                        streamId = id,
                        titulo = name,
                        tipo = tmdbType
                    )

                    val finalYear = if (tmdbInfo.year > 0) tmdbInfo.year else yearFromServer
                    if (finalYear < MIN_YEAR) continue

                    val finalIcon = if (tmdbInfo.posterUrl.isNotBlank()) tmdbInfo.posterUrl else icon

                    lista.add(
                        RecentItem(
                            streamId = id,
                            title = name,
                            icon = finalIcon,
                            extension = ext,
                            year = finalYear
                        )
                    )
                }
            } catch (_: Exception) {
            }

            withContext(Dispatchers.Main) {
                // Ordenar do ano mais novo para o mais antigo; se empatar, usa streamId
                val ordenada = lista
                    .sortedWith(
                        compareByDescending<RecentItem> { it.year }
                            .thenByDescending { it.streamId }
                    )
                onResult(ordenada)
            }
        }
    }

    /**
     * Modelo de info vinda do TMDB para cache/local.
     */
    data class TmdbInfo(
        val year: Int,
        val posterUrl: String
    )

    /**
     * Busca dados no TMDB usando cache em SharedPreferences.
     * Cache por streamId para evitar várias chamadas.
     */
    private fun buscarInfoTmdbComCache(
        context: Context,
        streamId: Int,
        titulo: String,
        tipo: String // "movie" ou "tv"
    ): TmdbInfo {
        val prefs = context.getSharedPreferences("tmdb_cache", Context.MODE_PRIVATE)
        val cacheKey = "tmdb_${tipo}_${streamId}"

        // Tenta cache primeiro
        val cached = prefs.getString(cacheKey, null)
        if (cached != null) {
            return try {
                val obj = JSONObject(cached)
                TmdbInfo(
                    year = obj.optInt("year", 0),
                    posterUrl = obj.optString("poster", "")
                )
            } catch (e: Exception) {
                TmdbInfo(0, "")
            }
        }

        // Se não tiver cache, chama TMDB rapidamente (com try/catch)
        return try {
            val encodedTitle = URLEncoder.encode(titulo, "UTF-8")
            val url =
                "$TMDB_BASE_URL/search/$tipo?api_key=$TMDB_API_KEY&language=pt-BR&query=$encodedTitle"

            val jsonTxt = URL(url).readText()
            val root = JSONObject(jsonTxt)
            val results = root.optJSONArray("results") ?: JSONArray()
            if (results.length() == 0) {
                TmdbInfo(0, "")
            } else {
                val first = results.getJSONObject(0)

                val dateField = if (tipo == "movie") {
                    first.optString("release_date", "")
                } else {
                    first.optString("first_air_date", "")
                }
                val year = dateField.take(4).toIntOrNull() ?: 0

                val posterPath = first.optString("poster_path", "")
                val posterUrl =
                    if (posterPath.isNotBlank()) "https://image.tmdb.org/t/p/w500$posterPath" else ""

                // Salva em cache
                val toSave = JSONObject()
                    .put("year", year)
                    .put("poster", posterUrl)
                prefs.edit().putString(cacheKey, toSave.toString()).apply()

                TmdbInfo(year = year, posterUrl = posterUrl)
            }
        } catch (_: Exception) {
            TmdbInfo(0, "")
        }
    }
}

/** Modelo simples para recentes (filmes ou séries) */
data class RecentItem(
    val streamId: Int,
    val title: String,
    val icon: String,
    val extension: String,
    val year: Int
)

/** Adapter usado pelas duas listas horizontais */
class RecentItemAdapter(
    private val items: List<RecentItem>,
    private val onClick: (RecentItem) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<RecentItemAdapter.VH>() {

    inner class VH(val view: android.view.View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {

        val imgPoster: android.widget.ImageView = view.findViewById(R.id.imgPosterRecent)
        val tvTitle: android.widget.TextView = view.findViewById(R.id.tvTitleRecent)
    }

    override fun onCreateViewHolder(
        parent: android.view.ViewGroup,
        viewType: Int
    ): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_movie, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title

        Glide.with(holder.view.context)
            .load(item.icon)
            .placeholder(R.mipmap.ic_launcher)
            .centerCrop()
            .into(holder.imgPoster)

        holder.view.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

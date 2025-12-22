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
import java.net.URL

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Receiver de downloads continua registrado em outro lugar (se precisar)

        setupClicks()
        setupRecentesRecycler()
    }

    override fun onResume() {
        super.onResume()
        carregarRecentesFilmes()
    }

    private fun setupClicks() {
        // TV AO VIVO
        binding.cardLiveTv.setOnClickListener {
            startActivity(Intent(this, LiveTvActivity::class.java))
        }

        // FILMES (VOD)
        binding.cardMovies.setOnClickListener {
            startActivity(Intent(this, VodActivity::class.java))
        }

        // SÉRIES
        binding.cardSeries.setOnClickListener {
            startActivity(Intent(this, SeriesActivity::class.java))
        }

        // Campo de busca no cabeçalho
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

        // Botão de configurações / menu
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

    private fun setupRecentesRecycler() {
        binding.rvRecentes.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentes.adapter = RecentMovieAdapter(emptyList()) { item ->
            abrirDetalheFilme(item)
        }
    }

    private fun abrirDetalheFilme(item: RecentMovie) {
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

    /**
     * Busca apenas FILMES recentes (movie) no servidor Xtream
     * e preenche o RecyclerView horizontal.
     */
    private fun carregarRecentesFilmes() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""
        val server = prefs.getString("server_url", "http://tvblack.shop") ?: "http://tvblack.shop"

        // endpoint típico de VOD recentes; ajuste se o seu for diferente
        val urlString =
            "$server/player_api.php?username=$user&password=$pass&action=get_vod_streams"

        CoroutineScope(Dispatchers.IO).launch {
            val lista = mutableListOf<RecentMovie>()

            try {
                val jsonTxt = URL(urlString).readText()
                val arr = JSONArray(jsonTxt)

                // pega só os primeiros 20 filmes (pode ajustar)
                for (i in 0 until minOf(arr.length(), 20)) {
                    val obj = arr.getJSONObject(i)

                    // filtra apenas tipo "movie" se existir o campo
                    val streamType = obj.optString("stream_type", "movie")
                    if (streamType != "movie") continue

                    val id = obj.getInt("stream_id")
                    val name = obj.optString("name", "Sem título")
                    val icon = obj.optString("stream_icon", "")
                    val ext = obj.optString("container_extension", "mp4")

                    lista.add(
                        RecentMovie(
                            streamId = id,
                            title = name,
                            icon = icon,
                            extension = ext
                        )
                    )
                }
            } catch (_: Exception) {
            }

            withContext(Dispatchers.Main) {
                val adapter = RecentMovieAdapter(lista) { item ->
                    abrirDetalheFilme(item)
                }
                binding.rvRecentes.adapter = adapter
            }
        }
    }
}

/** Modelo simples para filmes recentes */
data class RecentMovie(
    val streamId: Int,
    val title: String,
    val icon: String,
    val extension: String
)

/** Adapter do RecyclerView horizontal de “Adicionados Recentemente” */
class RecentMovieAdapter(
    private val items: List<RecentMovie>,
    private val onClick: (RecentMovie) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<RecentMovieAdapter.VH>() {

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

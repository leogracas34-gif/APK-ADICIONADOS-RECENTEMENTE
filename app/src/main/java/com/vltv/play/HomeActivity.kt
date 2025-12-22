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
import java.net.URL

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val MIN_YEAR = 2023 // só lançamentos a partir desse ano

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClicks()
        setupRecyclers()
    }

    override fun onResume() {
        super.onResume()
        carregarRecentMovies()
        carregarRecentSeries()
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

        binding.rvRecentSeries.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentSeries.adapter = RecentItemAdapter(emptyList()) { item ->
            abrirDetalhe(item)
        }
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
            expectedType = "movie"
        ) { lista ->
            binding.rvRecentMovies.adapter = RecentItemAdapter(lista) { item ->
                abrirDetalhe(item)
            }
        }
    }

    private fun carregarRecentSeries() {
        carregarRecentesGenerico(
            actionParam = "get_series",
            expectedType = "series"
        ) { lista ->
            binding.rvRecentSeries.adapter = RecentItemAdapter(lista) { item ->
                abrirDetalhe(item)
            }
        }
    }

    /**
     * Função genérica que lê do Xtream e filtra por tipo + ano.
     */
    private fun carregarRecentesGenerico(
        actionParam: String,
        expectedType: String,
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
                    if (streamType != expectedType) continue

                    val yearStr = obj.optString("year", "")
                    val year = yearStr.toIntOrNull() ?: 0
                    if (year < MIN_YEAR) continue

                    val id = obj.optInt("stream_id", 0)
                    val name = obj.optString("name", "Sem título")
                    val icon = obj.optString("stream_icon", "")
                    val ext = obj.optString("container_extension", "mp4")

                    lista.add(
                        RecentItem(
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
                onResult(lista)
            }
        }
    }
}

/** Modelo simples para recentes (filmes ou séries) */
data class RecentItem(
    val streamId: Int,
    val title: String,
    val icon: String,
    val extension: String
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

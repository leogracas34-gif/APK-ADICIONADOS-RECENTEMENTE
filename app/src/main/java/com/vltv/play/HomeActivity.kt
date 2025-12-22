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
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.vltv.play.databinding.ActivityHomeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

// MODELO PARA FILMES/SÉRIES RECENTES
data class ConteudoRecente(
    val tipo: String,          // "movie" ou "series"
    val id: Int,               // stream_id ou series_id
    val titulo: String,
    val capa: String?,
    val rating: Double,
    val lastModified: Long
)

// ADAPTER SIMPLES PARA A LISTA HORIZONTAL
class RecentAdapter(
    private val itens: List<ConteudoRecente>,
    private val onClick: (ConteudoRecente) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<RecentAdapter.VH>() {

    inner class VH(val binding: ItemRecentBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: android.view.ViewGroup,
        viewType: Int
    ): VH {
        val inflater = android.view.LayoutInflater.from(parent.context)
        val binding = ItemRecentBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = itens.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = itens[position]
        holder.binding.tvTitle.text = item.titulo
        holder.binding.tvType.text = if (item.tipo == "movie") "Filme" else "Série"

        val context = holder.itemView.context
        if (!item.capa.isNullOrEmpty()) {
            Glide.with(context)
                .load(item.capa)
                .transform(FitCenter())
                .into(holder.binding.imgCover)
        } else {
            holder.binding.imgCover.setImageResource(android.R.color.darker_gray)
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }
}

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    // BASE DO SERVIDOR (MESMO HOST DA SUA M3U)
    private val BASE_URL = "http://tvblack.shop"
    private val USERNAME = "241394"
    private val PASSWORD = "486576"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DownloadHelper.registerReceiver(this)

        setupRecycler()
        setupClicks()
    }

    override fun onResume() {
        super.onResume()
        carregarRecentesDoServidor()
    }

    private fun setupRecycler() {
        binding.rvRecent.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
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

    // NOVA FUNÇÃO: BUSCA FILMES + SÉRIES RECENTES DO SERVIDOR
    private fun carregarRecentesDoServidor() {
        val urlFilmes =
            "$BASE_URL/player_api.php?username=$USERNAME&password=$PASSWORD&action=get_vod_streams"
        val urlSeries =
            "$BASE_URL/player_api.php?username=$USERNAME&password=$PASSWORD&action=get_series"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val filmesJson = URL(urlFilmes).readText()
                val seriesJson = URL(urlSeries).readText()

                val filmesArray = JSONArray(filmesJson)
                val seriesArray = JSONArray(seriesJson)

                val lista = mutableListOf<ConteudoRecente>()

                // FILMES
                for (i in 0 until filmesArray.length()) {
                    val obj = filmesArray.getJSONObject(i)
                    val id = obj.optInt("stream_id")
                    val titulo = obj.optString("title", obj.optString("name", ""))
                    val cover = obj.optString("cover", null)
                    val rating = obj.optDouble("rating_5based", 0.0)
                    val lastMod = obj.optLong("last_modified", 0L)

                    lista.add(
                        ConteudoRecente(
                            tipo = "movie",
                            id = id,
                            titulo = titulo,
                            capa = cover,
                            rating = rating,
                            lastModified = lastMod
                        )
                    )
                }

                // SÉRIES
                for (i in 0 until seriesArray.length()) {
                    val obj = seriesArray.getJSONObject(i)
                    val id = obj.optInt("series_id")
                    val titulo = obj.optString("title", obj.optString("name", ""))
                    val cover = obj.optString("cover", null)
                    val rating = obj.optDouble("rating_5based", 0.0)
                    val lastMod = obj.optLong("last_modified", 0L)

                    lista.add(
                        ConteudoRecente(
                            tipo = "series",
                            id = id,
                            titulo = titulo,
                            capa = cover,
                            rating = rating,
                            lastModified = lastMod
                        )
                    )
                }

                // ORDENA POR MAIS RECENTE
                val ordenado = lista.sortedByDescending { it.lastModified }.take(30)

                withContext(Dispatchers.Main) {
                    binding.rvRecent.adapter =
                        RecentAdapter(ordenado) { item -> abrirDetalhes(item) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun abrirDetalhes(item: ConteudoRecente) {
        if (item.tipo == "movie") {
            val intent = Intent(this, DetailsActivity::class.java)
            intent.putExtra("stream_id", item.id)
            startActivity(intent)
        } else {
            val intent = Intent(this, SeriesDetailsActivity::class.java)
            intent.putExtra("series_id", item.id)
            startActivity(intent)
        }
    }
}

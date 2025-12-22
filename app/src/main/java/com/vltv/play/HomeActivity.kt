package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.vltv.play.databinding.ActivityHomeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    // MESMA BASE DA SUA LISTA (ajuste se mudar usuário/senha)
    private val BASE_URL = "http://tvblack.shop"
    private val USER = "241394"
    private val PASS = "486576"

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

        // Campo de busca
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

    private fun carregarRecentesDoServidor() {
        val urlFilmes =
            "$BASE_URL/player_api.php?username=$USER&password=$PASS&action=get_vod_streams"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonFilmesTxt = URL(urlFilmes).readText()
                val arrFilmes = JSONArray(jsonFilmesTxt)

                val lista = mutableListOf<ConteudoRecente>()

                for (i in 0 until arrFilmes.length()) {
                    val item = arrFilmes.getJSONObject(i)
                    val titulo = item.optString("title", item.optString("name", ""))
                    val capa = item.optString("streamicon", "")
                    val rating = item.optDouble("rating_5based", 0.0)
                    val added = item.optLong("added", 0L)
                    val id = item.optInt("stream_id", 0)

                    if (titulo.isNotBlank() && capa.isNotBlank() && id != 0) {
                        lista.add(
                            ConteudoRecente(
                                tipo = "movie",
                                id = id,
                                titulo = titulo,
                                capa = capa,
                                rating = rating,
                                lastModified = added
                            )
                        )
                    }
                }

                val recentes = lista
                    .sortedByDescending { it.lastModified }
                    .take(20)

                withContext(Dispatchers.Main) {
                    binding.rvRecent.adapter =
                        RecentesAdapter(recentes) { conteudo ->
                            abrirDetalhes(conteudo)
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun abrirDetalhes(item: ConteudoRecente) {
        // Por enquanto só filmes
        val intent = Intent(this, DetailsActivity::class.java)
        intent.putExtra("stream_id", item.id)
        startActivity(intent)
    }
}

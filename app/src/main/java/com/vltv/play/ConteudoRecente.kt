package com.vltv.play

data class ConteudoRecente(
    val tipo: String,      // "movie" (por enquanto usamos só filmes)
    val id: Int,           // stream_id
    val titulo: String,
    val capa: String,
    val rating: Double,
    val lastModified: Long
)

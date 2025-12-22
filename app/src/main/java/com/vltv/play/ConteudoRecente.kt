package com.vltv.play

data class ConteudoRecente(
    val tipo: String,      // "movie" ou "series"
    val id: Int,           // stream_id ou series_id
    val titulo: String,
    val capa: String,
    val rating: Double,
    val lastModified: Long
)

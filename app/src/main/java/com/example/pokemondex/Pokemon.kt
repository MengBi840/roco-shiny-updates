package com.example.pokemondex

data class Pokemon(
    val name: String,
    val type: String,
    val attr1: String = "",
    val attr2: String = "",
    val category: String = "",
    val img: String = ""
)

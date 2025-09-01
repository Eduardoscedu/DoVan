package com.example.dovan

// REMOVIDO: PESSOAL
enum class SectionType { DOCUMENTOS, VEICULO, ENDERECO, CONTATO }

data class Section(val iconRes: Int, val title: String, val type: SectionType)

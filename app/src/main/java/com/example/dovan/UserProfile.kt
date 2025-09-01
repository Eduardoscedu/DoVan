package com.example.dovan

data class FamilyProfile(
    val uid: String = "",
    val name: String = "",
    val cpf: String = "",
    val email: String = "",
    val telefone: String = "",
    val nascimento: Nascimento = Nascimento(),   // ALTERAÇÃO
    val role: String = "families",
    val createdAt: Long = System.currentTimeMillis()
)

data class DriverProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val telefone: String = "",
    val cpf: String = "",
    val nascimento: Nascimento = Nascimento(),   // ALTERAÇÃO
    val role: String = "drivers",
    val createdAt: Long = System.currentTimeMillis()
)


data class Nascimento(
    val dia: Int = 0,
    val mes: Int = 0,
    val ano: Int = 0
)

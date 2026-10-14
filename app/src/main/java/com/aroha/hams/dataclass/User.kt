package com.aroha.hams.dataclass

data class User(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val role: Role,
    val specialty: String = "",
    val address: String = "",
    val phoneNumber: String = ""
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")
}

enum class Role {
    DOCTOR,
    PATIENT
}

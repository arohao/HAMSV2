package com.aroha.hams.dataclass

data class Appointment(
    val id: String,
    val patientId: String,
    val patientName: String,
    val doctorId: String,
    val doctorName: String,
    val specialty: String,
    val date: String,
    val time: String,
    val status: String
)

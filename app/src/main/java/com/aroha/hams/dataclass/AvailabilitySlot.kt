package com.aroha.hams.dataclass

data class AvailabilitySlot(
    val id: String,
    val doctorId: String,
    val date: String,
    val time: String,
    val status: String
)

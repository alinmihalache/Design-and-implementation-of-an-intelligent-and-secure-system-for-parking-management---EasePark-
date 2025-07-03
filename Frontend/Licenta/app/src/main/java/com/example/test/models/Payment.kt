package com.example.test.models

import java.util.Date

data class Payment(
    val id: Long,
    val reservationId: Long,
    val amount: Double,
    val status: String,
    val createdAt: String
)

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED;

    override fun toString(): String = name.lowercase()
}

enum class PaymentMethod {
    CARD,
    CASH,
    TRANSFER
} 
package ru.agronum.ewcidshipping.model.postservice.russianpost

import java.math.BigDecimal

data class RussianPostTariff (
    val cost: BigDecimal,
    val deliveryTimeMin: Int,
    val deliveryTimeMax: Int
) {
    fun getTravelDays(): String {
        return "$deliveryTimeMin-$deliveryTimeMax дн.\t"
    }
}
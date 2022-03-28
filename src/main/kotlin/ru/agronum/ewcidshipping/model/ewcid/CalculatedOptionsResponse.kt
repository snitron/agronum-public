package ru.agronum.ewcidshipping.model.ewcid

import java.math.BigDecimal

data class CalculatedOptionsResponse (
    val shippingOptions: List<ShippingOption>
)

data class ShippingOption(
    val title: String,
    val rate: BigDecimal,
    val transitDays: String,
    val description: String
)
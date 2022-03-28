package ru.agronum.ewcidshipping.model.ewcid

import java.math.BigDecimal

data class OrderRequest (
    val storeId: Long,
    val cart: Cart
) {
    fun getWeightInGrams(): BigDecimal {
        return when(cart.weightUnit.lowercase()) {
            "kg" -> cart.weight * BigDecimal.TEN.pow(3)
            "g" -> cart.weight
            else -> cart.weight
        }
    }

    fun getTotal(): BigDecimal = cart.items.sumOf { it.price } - (cart.discount + cart.couponDiscount)
}

data class Cart(
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val couponDiscount: BigDecimal,
    val weight: BigDecimal,
    val weightUnit: String,
    val shippingAddress: Address,
    val items: List<Item>
)

data class Address(
    val street: String?,
    val city: String?,
    val countryCode: String?,
    val postalCode: String?,
    val stateOrProvinceCode: String?,
    val stateOrProvinceName: String?
) {
    fun getStringAddress(): String {
        return "${postalCode ?: ""}, ${city ?: ""}, ${street ?: ""}"
    }
}

data class Item(
    val price: BigDecimal
)
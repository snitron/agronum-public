package ru.agronum.ewcidshipping.model.postservice.ozonrocket

import com.ecwid.apiclient.v3.dto.order.result.FetchedOrder

data class OrderLine (
    val lineNumber: String,
    val articleNumber: String,
    val name: String,
    val weight: Int,
    val sellingPrice: Double,
    val estimatedPrice: Double = sellingPrice,
    val quantity: Int,
    var resideInPackages: Int = -1
        ) {
    constructor(weight: Int): this(
        lineNumber = "",
        articleNumber = "",
        name = "",
        weight = weight,
        sellingPrice = 0.0,
        quantity = 1
    )

    constructor(index: Int, orderItem: FetchedOrder.OrderItem, discount: Double = 0.0): this(
        lineNumber = index.toString(),
        name = orderItem.name ?: "",
        articleNumber = orderItem.productId?.toString() ?: "",
        sellingPrice = (orderItem.price ?: 0.0) - discount,
        estimatedPrice = (orderItem.price ?: 0.0) - discount,
        quantity = 1,
        weight = ((orderItem.weight ?: 0.0) * 1000).toInt()
    )

    fun toMap(): Map<Any, Any> = mapOf(
        "lineNumber" to lineNumber,
        "articleNumber" to articleNumber,
        "name" to name,
        "weight" to weight,
        "sellingPrice" to sellingPrice,
        "estimatedPrice" to estimatedPrice,
        "quantity" to quantity,
        "resideInPackages" to listOf(resideInPackages.toString())
    )
}
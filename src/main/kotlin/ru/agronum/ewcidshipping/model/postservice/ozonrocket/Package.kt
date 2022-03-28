package ru.agronum.ewcidshipping.model.postservice.ozonrocket

import com.google.gson.Gson
import java.math.BigDecimal

data class Package (
    val count: Int = 1,
    val dimensions: Dimension,
    var price: BigDecimal = BigDecimal.ZERO,
    var estimatedPrice: BigDecimal = BigDecimal.ZERO,
    var orderLines: List<OrderLine> = listOf()
        ) {
    fun toJson(price: BigDecimal, isOzon: Boolean): String {
        this.price = price
        this.estimatedPrice = price

        return Gson().toJson(PackageWrapper(this, isOzon))
    }

    fun getOrderMap(id: Int, isOzon: Boolean): Map<Any, Any> = mapOf(
        "packageNumber" to id.toString(),
        "dimensions" to DimensionWrapper(dimensions).getMapped()
    )

    fun copyWithWeight(weight: Int): Package {
        return this.copy(dimensions = dimensions.copy(weight = weight.toBigDecimal()))
    }
}

data class PackageWrapper (
    val count: Int = 1,
    val dimensions: DimensionWrapper,
    var price: BigDecimal = BigDecimal.ZERO,
    var estimatedPrice: BigDecimal = BigDecimal.ZERO
) {
    constructor(ppackage: Package, isOzon: Boolean): this(
        dimensions = DimensionWrapper(ppackage.dimensions),
        price = ppackage.price,
        estimatedPrice = ppackage.estimatedPrice
    )
}

data class Dimension(
    val weight: BigDecimal,
    val length: BigDecimal,
    val width: BigDecimal,
    val height: BigDecimal,
)

data class DimensionWrapper(
    val weight: Long,
    val length: Long,
    val width: Long,
    val height: Long,
) {
    constructor(dimensions: Dimension): this(
        weight = dimensions.weight.toLong(),
        length = dimensions.length.toLong(),
        width = dimensions.width.toLong(),
        height = dimensions.height.toLong()
    )

    fun getMapped(): Map<Any, Any> = mapOf(
        "weight" to weight,
        "length" to length * 10,
        "width" to width * 10,
        "height" to height * 10
    )
}

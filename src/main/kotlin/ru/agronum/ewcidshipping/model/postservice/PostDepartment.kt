package ru.agronum.ewcidshipping.model.postservice

import org.json.JSONObject
import java.math.BigDecimal

abstract class PostDepartment {
    abstract val address: String
    abstract val name: String
}

data class RussianPostDepartment(
    override val address: String,
    override val name: String,
    val region: String,
    val postalCode: String,
    val isClosed: Boolean
    ) : PostDepartment() {
        constructor(json: JSONObject): this(
            postalCode = json.getString("postal-code"),
            address = "${json.getString("region")}, ${json.getString("settlement")}, ${json.getString("address-source")}",
            name = "",
            isClosed = json.getBoolean("is-closed"),
            region = json.getString("region")
        )

    fun getDescription(cost: BigDecimal? = null): String = "${if(cost != null) "$cost рублей при получении. " else ""}$postalCode, $address"
}
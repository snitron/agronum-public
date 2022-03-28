package ru.agronum.ewcidshipping.model.postservice.ozonrocket

import java.math.BigDecimal

data class OzonRocketPostTariff (
    val id: Long = -1L,
    val address: String = "",
    val cost: BigDecimal,
    val type: OzonRocketPostDepartmentType,
    val deliverInDays: String,
    val region: String = "",
    val street: String = "",
    val city: String = "",
    val placement: String = "",
    val isCardAllowed: Boolean = false,
    val isCashAllowed: Boolean = false
        ) {
    fun getTitle(
        pickpointTitle: String,
        courierTitle: String,
        postamatTitle: String
    ): String {
        return when (type) {
            OzonRocketPostDepartmentType.PICKPOINT -> "$pickpointTitle, #$id"
            OzonRocketPostDepartmentType.COURIER -> "$courierTitle, #$id"
            OzonRocketPostDepartmentType.POSTAMAT -> "$postamatTitle, #$id"
        }
    }

    fun getDescription(
        noCardTitle: String,
        noCashTitle: String,
        noCardAndCashTitle: String
    ): String = "$deliverInDays дн.\t${
            if (!isCardAllowed && !isCashAllowed) noCardAndCashTitle
            else if (!isCardAllowed) noCardTitle
            else if (!isCashAllowed) noCashTitle
            else ""
    } $address"
}

enum class OzonRocketPostDepartmentType (val value: String) {
    PICKPOINT("PickPoint"), COURIER("Courier"), POSTAMAT("Postamat")
}
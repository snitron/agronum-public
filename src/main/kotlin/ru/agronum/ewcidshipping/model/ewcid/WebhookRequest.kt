package ru.agronum.ewcidshipping.model.ewcid

data class WebhookRequest (
    val eventType: String,
    val data: WebhookRequestData,
    val entityId: Int
)

data class WebhookRequestData(
    val orderId: String
)
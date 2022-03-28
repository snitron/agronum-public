package ru.agronum.ewcidshipping.ecwid

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import ru.agronum.ewcidshipping.api.ecwid.EcwidAPI
import ru.agronum.ewcidshipping.api.ozon.OzonRocketAPI
import ru.agronum.ewcidshipping.api.russianpost.RussianPostAPI
import ru.agronum.ewcidshipping.model.ewcid.WebhookRequest
import ru.agronum.ewcidshipping.model.postservice.ozonrocket.OrderLine
import ru.agronum.ewcidshipping.utils.DebugUtils
import java.lang.Double.max
import java.lang.Double.min

@RestController
class AgronumWebhook(
    @Value("\${russian-post.carrier-name}")
    val russianPostCarrierName: String,
    @Value("\${ozon-rocket.carrier-name}")
    val ozonRocketCarrierName: String,
    @Value("\${russian-post.nal-title}")
    val russianPostNalTitle: String,
    @Value("\${russian-post.paid-title}")
    val russianPostPaidTitle: String,
    @Value("\${ozon-rocket.pickpoint}")
    val pickpointTitle: String,
    @Value("\${ozon-rocket.courier}")
    val courierTitle: String,
    @Value("\${ozon-rocket.postamat}")
    val postamatTitle: String,
    @Value("\${agronum.webhook.authorization}")
    val authorizationHeader: String,
    val ecwidAPI: EcwidAPI,
    val russianPostAPI: RussianPostAPI,
    val ozonRocketAPI: OzonRocketAPI
) {
    private val checkedOrders = hashSetOf<String>()

    companion object {
        fun getStreet(street: String): String {
            val splitted = street.split(" ")

            return if (splitted.size > 1) {
                val s = StringBuilder()

                for (i in 0 until splitted.size - 1) {
                    s.append(splitted[i])
                    s.append(" ")
                }

                s.toString()
            } else {
                ""
            }
        }
    }

    @PostMapping("/webhook")
    fun processNewOrder(
        @RequestHeader("Authorization")
        authorization: String,
        @RequestBody
        webhookRequest: WebhookRequest
    ): ResponseEntity<Any> {
        assert(authorization == authorizationHeader) { "Not allowed" }

        DebugUtils.print(webhookRequest.toString())

        if (webhookRequest.eventType == "order.created" && webhookRequest.data.orderId in checkedOrders) {

            val order = ecwidAPI.getOrder(webhookRequest.data.orderId.toInt())

            val shippingOption = order.shippingOption!!
            val address = order.shippingPerson!!

            var sum = order.items?.map { (it.price ?: 0.0) * (it.quantity ?: 1) }?.sum() ?: 0.0
            var discount = (order.discount ?: 0.0) + (order.couponDiscount ?: 0.0)

            sum = max(0.0, sum - discount)

            val pckgs = mutableListOf<OrderLine>()
            for (i in order.items!!.indices) {
                for (j in 0 until (order.items!![i].quantity ?: 1)) {
                    val ol = OrderLine(
                        i, order.items!![i], if (discount > 0) {
                            val d = min(discount, order.items!![i].price ?: 0.0)
                            discount -= d

                            d
                        } else {
                            0.0
                        }
                    )
                    pckgs.add(ol)
                }
            }

            val calculatedPackages = OzonRocketAPI.calculatePackages(pckgs)

            val weight = calculatedPackages.sumOf { it.dimensions.weight }

            val needsPayment = order.paymentMethod != "Банковской картой на сайте"

            if (shippingOption.shippingCarrierName == russianPostCarrierName) {
                GlobalScope.launch {
                    val russianPostDepartment = russianPostAPI.getDepartmentByPostalCode(address.postalCode!!)

                    val orderId = russianPostAPI.postOrder(
                        deliveryCost = (shippingOption.shippingRateWithoutTax * 100).toLong(),
                        amount = (order.subtotal!! * 100).toLong(),
                        name = address.name ?: "",
                        middleName = "",
                        surname = address.lastName ?: "",
                        postIndex = address.postalCode ?: "0",
                        isNal = russianPostNalTitle.contains(shippingOption.shippingMethodName ?: ""),
                        buildingNumber = (address.street ?: "").split(" ").last(),
                        weight = weight.toLong(),
                        id = webhookRequest.data.orderId,
                        place = address.city ?: "",
                        street = getStreet(address.street ?: ""),
                        region = russianPostDepartment.region,
                        phoneNum = address.phone!!.filter { it.isDigit() }.toLong(),
                        rawAddress = "${address.postalCode ?: ""}, ${russianPostDepartment.region}, ${address.city ?: ""}, ${address.street ?: ""}"
                    )

                    val trackingNumber = russianPostAPI.getOrderTrackingNumber(orderId)

                    if (trackingNumber != null) {
                        ecwidAPI.addTrackNumber(webhookRequest.data.orderId.toInt(), trackingNumber)
                    }
                }
            } else if (shippingOption.shippingCarrierName == ozonRocketCarrierName) {
                val shippingMethod = shippingOption.shippingMethodName ?: return ResponseEntity.ok().build()
                val id = shippingMethod.split("#")[1]

                GlobalScope.launch {
                    val tariff = ozonRocketAPI.getTariff(id)

                    val orderId = ozonRocketAPI.postOrder(
                        userName = address.name ?: "",
                        rawAddress = tariff.address,
                        amount = sum,
                        packages = calculatedPackages,
                        ozonDeliveryNumber = id,
                        email = order.email ?: "",
                        id = webhookRequest.data.orderId,
                        phoneNum = address.phone ?: "",
                        deliveryPrice = (order.shippingOption?.shippingRate ?: 0.0) - discount,
                        needsPayment = needsPayment
                    )

                    if (orderId != null) {
                        ecwidAPI.addTrackNumber(webhookRequest.data.orderId.toInt(), orderId)
                    } else {
                        ecwidAPI.cancelOrder(webhookRequest.data.orderId.toInt())
                    }
                }
            }
        } else {
            checkedOrders.add(webhookRequest.data.orderId)
        }

        return ResponseEntity.ok().build()
    }
}
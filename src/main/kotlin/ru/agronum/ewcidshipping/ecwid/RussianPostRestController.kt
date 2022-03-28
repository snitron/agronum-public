package ru.agronum.ewcidshipping.ecwid

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.agronum.ewcidshipping.api.ozon.OzonRocketAPI
import ru.agronum.ewcidshipping.api.russianpost.RussianPostAPI
import ru.agronum.ewcidshipping.api.russianpost.RussianPostTariffAPI
import ru.agronum.ewcidshipping.model.ewcid.CalculatedOptionsResponse
import ru.agronum.ewcidshipping.model.ewcid.OrderRequest
import ru.agronum.ewcidshipping.model.ewcid.ShippingOption
import ru.agronum.ewcidshipping.model.ewcidorder.EcwidOrder
import ru.agronum.ewcidshipping.model.ewcidorder.EcwidOrderService
import ru.agronum.ewcidshipping.model.ewcidorder.EcwidOrderStatus
import ru.agronum.ewcidshipping.model.postservice.PostServiceType
import ru.agronum.ewcidshipping.model.postservice.russianpost.RussianPostSendType
import java.math.BigDecimal
import java.util.*

@RestController
class RussianPostRestController (
    @Value("\${russian-post.postal-code}")
    val postalCode: String,
    @Value("\${russian-post.paid-title}")
    val paidTitle: String,
    @Value("\${russian-post.nal-title}")
    val nalTitle: String,
    val ecwidOrderService: EcwidOrderService,
    val russianPostAPI: RussianPostAPI,
    val russianPostTariffAPI: RussianPostTariffAPI
        ) {
    private val jobs = mutableMapOf<String, Job>()
    private val discounts = mutableMapOf<String, Double>()

    @PostMapping("/russianpost")
    fun processOrder(@RequestBody orderRequest: OrderRequest): CalculatedOptionsResponse {
        //DebugUtils.print(orderRequest)


        val order = ecwidOrderService.saveEcwidOrder(
            EcwidOrder(
                ecwidId = 1234L,
                orderStatus = EcwidOrderStatus.CREATED,
                postService = PostServiceType.RUSSIAN_POST,
                sum = (orderRequest.cart.subtotal * BigDecimal.TEN.pow(2)).toLong(),
                weight = orderRequest.getWeightInGrams()
            )
        )

        val shippingOptions = Collections.synchronizedList(arrayListOf<ShippingOption>())
        val calculatedPackages = OzonRocketAPI.calculatePackages(order.weight.toInt())
        val weight = calculatedPackages.sumOf { it.dimensions.weight }.toLong()

        runBlocking {
            val postOffices = russianPostAPI.getDepartmentsByAddress(
                russianPostAPI.getPostalCodesByAddress(
                    orderRequest.cart.shippingAddress.getStringAddress()
                )
            )

            for (postOffice in postOffices) {
                launch {
                    val paidTariff = russianPostTariffAPI.calculateSendTariff(
                        postCodeFrom = postalCode,
                        postCodeTo = postOffice.postalCode,
                        weight = weight,
                        type = RussianPostSendType.PAID
                    )

                    if (paidTariff != null) {
                        synchronized(shippingOptions) {
                            shippingOptions.add(
                                ShippingOption(
                                    title = paidTitle,
                                    rate = paidTariff.cost,
                                    transitDays = paidTariff.getTravelDays(),
                                    description = paidTariff.getTravelDays() + " " + postOffice.getDescription()
                                )
                            )
                        }
                    }
                }

                launch {
                    val nalozhTariff = russianPostTariffAPI.calculateSendTariff(
                        postCodeFrom = postalCode,
                        postCodeTo = postOffice.postalCode,
                        weight = weight,
                        sum = order.sum!!.toLong(),
                        type = RussianPostSendType.NAL_PL
                    )

                    if (nalozhTariff != null) {
                        synchronized(shippingOptions) {
                            shippingOptions.add(
                                ShippingOption(
                                    title = nalTitle,
                                    rate = nalozhTariff.cost,
                                    transitDays = nalozhTariff.getTravelDays(),
                                    description = nalozhTariff.getTravelDays() + " " + postOffice.getDescription(orderRequest.getTotal() + nalozhTariff.cost)
                                )
                            )
                        }
                    }
                }
            }
        }

        return CalculatedOptionsResponse(shippingOptions)
    }
}
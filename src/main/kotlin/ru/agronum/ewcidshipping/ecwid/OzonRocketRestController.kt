package ru.agronum.ewcidshipping.ecwid

import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.agronum.ewcidshipping.api.ozon.OzonRocketAPI
import ru.agronum.ewcidshipping.model.ewcid.CalculatedOptionsResponse
import ru.agronum.ewcidshipping.model.ewcid.OrderRequest
import ru.agronum.ewcidshipping.model.ewcid.ShippingOption
import ru.agronum.ewcidshipping.utils.DebugUtils

@RestController
class OzonRocketRestController (
    @Value("\${ozon-rocket.pickpoint}")
    val pickpointTitle: String,
    @Value("\${ozon-rocket.courier}")
    val courierTitle: String,
    @Value("\${ozon-rocket.postamat}")
    val postamatTitle: String,
    @Value("\${ozon-rocket.no-card}")
    val noCardTitle: String,
    @Value("\${ozon-rocket.no-cash}")
    val noCashTitle: String,
    @Value("\${ozon-rocket.no-card-and-cash}")
    val noCardAndCashTitle: String,
    val ozonRocketAPI: OzonRocketAPI
        ) {
    @PostMapping("/ozonrocket")
    fun processOrder(@RequestBody orderRequest: OrderRequest): CalculatedOptionsResponse {
        DebugUtils.print(orderRequest.toString(), "")
        val shippingOptions = runBlocking {
            val packages = OzonRocketAPI.calculatePackages(orderRequest.getWeightInGrams().toInt())
            return@runBlocking ozonRocketAPI.getTariffs(
                address = orderRequest.cart.shippingAddress.getStringAddress(),
                packages = packages,
                averagePrice = orderRequest.cart.subtotal / packages.size.toBigDecimal()
            ).map {
                ShippingOption(
                    rate = it.cost,
                    transitDays = it.deliverInDays,
                    title = it.getTitle(
                        pickpointTitle, courierTitle, postamatTitle
                    ),
                    description = it.getDescription(noCardTitle, noCashTitle, noCardAndCashTitle)
                )
            }
        }

        return CalculatedOptionsResponse(shippingOptions)
    }
}
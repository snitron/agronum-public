package ru.agronum.ewcidshipping.api.ecwid

import com.ecwid.apiclient.v3.ApiClient
import com.ecwid.apiclient.v3.config.ApiServerDomain
import com.ecwid.apiclient.v3.config.ApiStoreCredentials
import com.ecwid.apiclient.v3.dto.order.enums.OrderFulfillmentStatus
import com.ecwid.apiclient.v3.dto.order.enums.OrderPaymentStatus
import com.ecwid.apiclient.v3.dto.order.request.OrderDetailsRequest
import com.ecwid.apiclient.v3.dto.order.request.OrderInvoiceRequest
import com.ecwid.apiclient.v3.dto.order.request.OrderUpdateRequest
import com.ecwid.apiclient.v3.dto.order.request.UpdatedOrder
import com.ecwid.apiclient.v3.dto.order.result.FetchedOrder
import com.ecwid.apiclient.v3.httptransport.impl.ApacheCommonsHttpClientTransport
import com.ecwid.apiclient.v3.jsontransformer.gson.GsonTransformerProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EcwidAPI (
    @Value("\${agronum.store-id}")
    val storeId: String,
    @Value("\${agronum.token}")
    val token: String,
    @Value("\${agronum.no-nal-pl-comment}")
    val noNalPlComment: String,
    val apiClient: ApiClient = ApiClient.create(
        apiServerDomain = ApiServerDomain(),
        storeCredentials = ApiStoreCredentials(
            storeId = storeId.toInt(),
            apiToken = token
        ),
        httpTransport = ApacheCommonsHttpClientTransport(),
        jsonTransformerProvider = GsonTransformerProvider()

    )
        ) {
    fun getOrder(number: Int): FetchedOrder {
        return apiClient.getOrderDetails(OrderDetailsRequest(number))
    }

    fun addTrackNumber(number: Int, trackNum: String) {
        apiClient.updateOrder(OrderUpdateRequest(
            number,
            UpdatedOrder(trackingNumber = trackNum, fulfillmentStatus = OrderFulfillmentStatus.AWAITING_PROCESSING)
        ))
    }

    fun cancelOrder(number: Int) {
        apiClient.updateOrder(OrderUpdateRequest(
            number,
            UpdatedOrder(orderComments = noNalPlComment, fulfillmentStatus = OrderFulfillmentStatus.WILL_NOT_DELIVER, paymentStatus = OrderPaymentStatus.CANCELLED)
        ))
    }
}
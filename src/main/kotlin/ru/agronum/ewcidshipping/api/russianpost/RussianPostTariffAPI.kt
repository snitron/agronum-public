package ru.agronum.ewcidshipping.api.russianpost

import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component
import ru.agronum.ewcidshipping.model.postservice.russianpost.RussianPostSendType
import ru.agronum.ewcidshipping.model.postservice.russianpost.RussianPostTariff
import ru.agronum.ewcidshipping.utils.DateUtils
import ru.agronum.ewcidshipping.utils.OkHttpUtils
import utils.retry
import java.math.BigDecimal


@Component
class RussianPostTariffAPI {
    companion object {
        private const val BASE_API = "https://tariff.pochta.ru/v2"

        private fun urlForTariff(
            postCodeFrom: String,
            postCodeTo: String,
            weight: Long,
            sum: Long?,
            type: RussianPostSendType
        ): String {
            return "$BASE_API/calculate/tariff/delivery?json&object=${
                when (type) {
                    RussianPostSendType.PAID -> "23030"
                    RussianPostSendType.NAL_PL -> "23040"
                }
            }&from=$postCodeFrom" +
                    "&to=$postCodeTo" +
                    "&weight=$weight" +
                    "&group=0" +
                    "&closed=1" +
                    if (type == RussianPostSendType.NAL_PL) {
                        "&sumoc=$sum"
                    } else {
                        ""
                    } +
                    "&date=${DateUtils.getRussianPostTariffDate()}" //+
                    //"&time=${DateUtils.getRussianPostTariffTime()}"
        }
    }
    private val httpClient = OkHttpClient.Builder()
        .build()

    suspend fun calculateSendTariff(postCodeFrom: String,
                                    postCodeTo: String,
                                    weight: Long,
                                    sum: Long? = null,
                                    type: RussianPostSendType
    ): RussianPostTariff? {
        if (type == RussianPostSendType.NAL_PL && sum == 0L) { return null }

        val data = retry {
            OkHttpUtils.makeAsyncRequest(
                client = httpClient,
                    request = Request.Builder()
                        .url(urlForTariff(
                            postCodeFrom, postCodeTo, weight, sum, type
                        ))
                        .get()
                        .build(),
                s = urlForTariff(
                    postCodeFrom, postCodeTo, weight, sum, type
                )
            )
        }

        return RussianPostTariff(
            cost = data!!.getBigDecimal("paynds") / BigDecimal.TEN.pow(2),
            deliveryTimeMin = data.getJSONObject("delivery").getInt("min"),
            deliveryTimeMax = data.getJSONObject("delivery").getInt("max")
        )
    }
}
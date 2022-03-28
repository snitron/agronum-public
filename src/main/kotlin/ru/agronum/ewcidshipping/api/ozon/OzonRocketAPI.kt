package ru.agronum.ewcidshipping.api.ozon

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.hibernate.criterion.Order
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.agronum.ewcidshipping.api.russianpost.RussianPostAPI
import ru.agronum.ewcidshipping.model.postservice.ozonrocket.*
import ru.agronum.ewcidshipping.utils.OkHttpUtils
import ru.agronum.ewcidshipping.utils.getStringOrNull
import java.lang.Integer.min
import java.math.BigDecimal
import java.time.Duration

@Component
class OzonRocketAPI(
    @Value("\${ozon-rocket.client-id}")
    val clientId: String,
    @Value("\${ozon-rocket.client-secret}")
    val clientSecret: String,
    @Value("\${ozon-rocket.test-client-id}")
    val testClientId: String,
    @Value("\${ozon-rocket.test-client-secret}")
    val testClientSecret: String,
    @Value("\${ozon-rocket.from-id}")
    val fromId: Long
) {
    companion object {
        private const val PROD_BASE_URL = "https://xapi.ozon.ru/principal-integration-api/v1"
        private const val PROD_TOKEN_URL = "https://xapi.ozon.ru/principal-auth-api/connect/token"

        private const val TEST_BASE_URL = "https://api-stg.ozonru.me/principal-integration-api/v1"
        private const val TEST_TOKEN_URL = "https://api-stg.ozonru.me/principal-auth-api/connect/token"

        private const val TEST = false

        val TOKEN_URL = if (TEST) TEST_TOKEN_URL else PROD_TOKEN_URL
        val BASE_URL =  if (TEST) TEST_BASE_URL else PROD_BASE_URL

        const val MAX_DELIVERY_VARIANTS = 10

        //ALL IN GRAMS
        private val BOXES = mutableListOf(
            0..1000 to (Package(
                dimensions = Dimension(
                    weight = BigDecimal.ZERO,
                    length = 17.0.toBigDecimal(),
                    width = 10.0.toBigDecimal(),
                    height = 8.0.toBigDecimal()
                )
            ) to 100),

            1000..3000 to (Package(
                dimensions = Dimension(
                    weight = BigDecimal.ZERO,
                    length = 30.0.toBigDecimal(),
                    width = 15.0.toBigDecimal(),
                    height = 10.0.toBigDecimal()
                )
            ) to 250),

            3000..5000 to (Package(
                dimensions = Dimension(
                    weight = BigDecimal.ZERO,
                    length = 30.0.toBigDecimal(),
                    width = 15.0.toBigDecimal(),
                    height = 15.0.toBigDecimal()
                )
            ) to 250),

            5000..10000 to (Package(
                dimensions = Dimension(
                    weight = BigDecimal.ZERO,
                    length = 30.0.toBigDecimal(),
                    width = 15.0.toBigDecimal(),
                    height = 15.0.toBigDecimal()
                )
            ) to 250),

            10000..15000 to (Package(
                dimensions = Dimension(
                    weight = BigDecimal.ZERO,
                    length = 40.0.toBigDecimal(),
                    width = 30.0.toBigDecimal(),
                    height = 20.0.toBigDecimal()
                )
            ) to 300),
            15000..Int.MAX_VALUE to (Package(
                dimensions = Dimension(
                    weight = BigDecimal.ZERO,
                    length = 40.0.toBigDecimal(),
                    width = 30.0.toBigDecimal(),
                    height = 20.0.toBigDecimal()
                )
            ) to 300)
        ).reversed()

        private fun getBoxByWeight(weight: Int): Pair<Package, Int> {
            for (b in BOXES) {
                if (weight in b.first) {
                    return b.second
                }
            }

            return BOXES.first().second
        }

        fun calculatePackages(weight: Int): List<Package> {
            return calculatePackages(listOf(OrderLine(weight)))
        }
        //Grams
        fun calculatePackages(weights: List<OrderLine>): List<Package> {
            val packages = arrayListOf<Package>()
            val sortedWeights = weights.toMutableList().sortedByDescending { it.weight }
            var s = 0

            while (true) {
                var w = 0
                val lines = mutableListOf<OrderLine>()
                var got = false
                val was0 = s == 0

                for (i in s until sortedWeights.size) {
                    if (w + sortedWeights[i].weight > BOXES.first().first.last) {
                        got = true
                        break
                    } else {
                        lines.add(sortedWeights[i])
                        w += sortedWeights[i].weight
                        s++
                    }
                }

                if (got || s == sortedWeights.size && was0) {
                    packages.add(
                        getBoxByWeight(w).let { pair ->
                            pair.first.copyWithWeight(w + pair.second).apply {
                                orderLines = lines.map { it.resideInPackages = packages.size + 1; it }
                            }
                        }
                    )
                } else {
                    break
                }
            }

            return packages
        }
    }


    lateinit var token: String
    val httpClient = OkHttpProvider().httpClient

    inner class OkHttpProvider {
        val httpClient = OkHttpClient.Builder()
            .addNetworkInterceptor(TokenInterceptor())
            .connectTimeout(Duration.ofMinutes(1))
            .build()

        inner class TokenInterceptor() : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val newReq = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                return chain.proceed(newReq)
            }
        }
    }

    suspend fun getToken(): String {
        return OkHttpUtils.makeAsyncRequest(
            OkHttpClient.Builder().build(),
            Request.Builder()
                .url(TOKEN_URL)
                .post("grant_type=client_credentials&client_id=${if (TEST) testClientId else clientId}&client_secret=${if (TEST) testClientSecret else clientSecret}"
                    .toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()!!))
                .build()
        )!!.getString("access_token")
    }

    suspend fun getTariffs(
        address: String,
        packages: List<Package>,
        averagePrice: BigDecimal
    ): List<OzonRocketPostTariff> {
        val departments = mutableMapOf<OzonRocketPostDepartmentType, Pair<BigDecimal, List<String>>>()
        val jsonDepartments = arrayListOf<JSONObject>()

        for (type in OzonRocketPostDepartmentType.values()) {
            token = getToken()
            val requestString = """
                    {
                     "deliveryType": "${type.value}",
                      "filter": {
                        "isCashAvailable": true,
                        "isPaymentCardAvailable": true,
                        "isAnyPaymentAvailable": true
                      },
                     "address": "$address",
                     "radius": 25,
                     "packages": [
                        ${packages.joinToString(",") { it.toJson(averagePrice, false) }}
                     ]
                    }
        """.trimIndent()
            val json = OkHttpUtils.makeAsyncRequest(
                httpClient,
                Request.Builder()
                    .url("$BASE_URL/delivery/variants/byaddress")
                    .post(
                        requestString.toRequestBody(OkHttpUtils.JSON_TYPE)
                    )
                    .build(),
                requestString
            )

            val jsonArray = json!!.getJSONArray("data")

            for (i in 0 until min(MAX_DELIVERY_VARIANTS, jsonArray.length())) {
                jsonDepartments.add(jsonArray.getJSONObject(i))
            }
        }

        val tariffs = getDeliveryPrices(
            address = address,
            packages = packages,
            averagePrice = averagePrice
        )

        val upgradedTariffs = arrayListOf<OzonRocketPostTariff>()

        for (jsonObject in jsonDepartments) {
            val id = jsonObject.getLong("id")
            val name = jsonObject.getString("name")
            val region = jsonObject.getStringOrNull("region")
            val settlement = jsonObject.getStringOrNull("settlement")
            val street = jsonObject.getStringOrNull("streets")
            val placement = jsonObject.getStringOrNull("placement")

            val isCardAllowed = jsonObject.getBoolean("cardPaymentAvailable")
            val isCashAllowed = !jsonObject.getBoolean("isCashForbidden")

            val tariff = when (jsonObject.getString("objectTypeName").lowercase()) {
                "самовывоз" -> {
                   tariffs.first { it.type == OzonRocketPostDepartmentType.PICKPOINT }
                }

                "курьерская" -> {
                    tariffs.first { it.type == OzonRocketPostDepartmentType.COURIER }
                }

                "постамат" -> {
                    tariffs.first { it.type == OzonRocketPostDepartmentType.POSTAMAT }
                }

                else -> null
            }

            if (tariff != null) {
                upgradedTariffs.add(tariff.copy(
                    id = id,
                    address = name,
                    region = region ?: "",
                    city = settlement ?: "",
                street = street ?: "",
                placement = placement ?: "",
                isCardAllowed = isCardAllowed,
                isCashAllowed = isCashAllowed))
            }

        }

        return upgradedTariffs
    }

    suspend fun getTariff(
        id: String
    ): OzonRocketPostTariff {
        val departments = mutableMapOf<OzonRocketPostDepartmentType, Pair<BigDecimal, List<String>>>()
        val jsonDepartments = arrayListOf<JSONObject>()

        token = getToken()
        val requestString = """{"ids": [$id]}"""

        val jsonObject = OkHttpUtils.makeAsyncRequest(
            httpClient,
            Request.Builder()
                .url("$BASE_URL/delivery/variants/byIds")
                .post(
                    requestString.toRequestBody(OkHttpUtils.JSON_TYPE)
                )
                .build(),
            requestString
        )!!.getJSONArray("data").getJSONObject(0)

        val name = jsonObject.getString("name")
        val region = jsonObject.getString("region")
        val settlement = jsonObject.getString("settlement")
        val street = jsonObject.getString("streets")
        val placement = jsonObject.getString("placement")

        return OzonRocketPostTariff(
                id = id.toLong(),
                address = name,
                region = region,
                city = settlement,
                street = street,
                placement = placement,
                cost = BigDecimal.ZERO,
                type = OzonRocketPostDepartmentType.PICKPOINT,
                deliverInDays = ""
        )
    }

    private suspend fun getDeliveryPrices(
        address: String,
        packages: List<Package>,
        averagePrice: BigDecimal
    ): List<OzonRocketPostTariff> {
        token = getToken()

        val requestString = """
                    {
                     "fromPlaceId": $fromId,
                     "destinationAddress": "$address",
                     "packages": [
                        ${packages.joinToString(",") { it.toJson(averagePrice, false) }}
                     ]
                    }
                """.trimIndent()

        val json = OkHttpUtils.makeAsyncRequest(
            httpClient,
            Request.Builder()
                .url("$BASE_URL/delivery/calculate/information")
                .post(
                    requestString.toRequestBody(OkHttpUtils.JSON_TYPE)
                )
                .build(),
            requestString
        )!!

        val jsonArray = json.getJSONArray("deliveryInfos")
        val tariffs = arrayListOf<OzonRocketPostTariff>()

        for (i in 0 until jsonArray.length()) {
            val jsonTariff = jsonArray.getJSONObject(i)

            tariffs.add(
                OzonRocketPostTariff(
                    type = OzonRocketPostDepartmentType.valueOf(jsonTariff.getString("deliveryType").uppercase()),
                    cost = jsonTariff.getBigDecimal("price"),
                    deliverInDays = jsonTariff.getInt("deliveryTermInDays").toString()
                )
            )
        }

        return tariffs
    }

    suspend fun postOrder(
        amount: Double,
        userName: String,
        id: String,
        rawAddress: String,
        phoneNum: String,
        email: String,
        ozonDeliveryNumber: String,
        packages: List<Package>,
        deliveryPrice: Double,
        needsPayment: Boolean
    ): String? {
        val requestString = JSONObject(
            mapOf(
                "orderNumber" to id,
                "buyer" to mapOf(
                    "name" to userName,
                    "phone" to phoneNum,
                    "email" to email,
                    "type" to "NaturalPerson"
                ),
                "payment" to mapOf(
                    "type" to if (needsPayment) "Postpay" else "FullPrepayment",
                    "prepaymentAmount" to if (needsPayment) 0.0 else String.format("%.2f", amount),
                    "recipientPaymentAmount" to if (needsPayment) String.format("%.2f", amount + deliveryPrice) else 0,
                    "deliveryPrice" to if (needsPayment) String.format("%.2f", deliveryPrice) else 0
                ),
                "deliveryInformation" to mapOf(
                    "deliveryVariantId" to ozonDeliveryNumber,
                    "address" to rawAddress
                ),
                "firstMileTransfer" to mapOf(
                    "type" to "PickUp",
                    "fromPlaceId" to fromId.toString()
                ),
                "orderLines" to packages.map { it.orderLines }.flatten().let { it.map { it.toMap() } },
                "packages" to packages.mapIndexed { index, p -> p.getOrderMap(index + 1, true) }
            )
        ).toString()

        return OkHttpUtils.makeAsyncRequest(
            client = OkHttpProvider().httpClient,
            request = Request.Builder()
                .url("$BASE_URL/order/")
                .post(
                    requestString.toRequestBody(OkHttpUtils.JSON_TYPE)
                )
                .build(),
            s = requestString
        )?.getString("logisticOrderNumber")
    }
}
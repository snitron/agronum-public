package ru.agronum.ewcidshipping.api.russianpost

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.agronum.ewcidshipping.model.postservice.RussianPostDepartment
import ru.agronum.ewcidshipping.utils.OkHttpUtils
import ru.agronum.ewcidshipping.utils.fromJson
import utils.retry


@Component
class RussianPostAPI(
    @Value("\${russian-post.token}")
    val token: String,
    @Value("\${russian-post.authorization-key}")
    val authorizationKey: String
) {
    companion object {
        const val BASE_URL = "https://otpravka-api.pochta.ru"
    }

    inner class OkHttpProvider {
        val httpClient = OkHttpClient.Builder()
            .addNetworkInterceptor(TokenInterceptor())
            .build()

        inner class TokenInterceptor() : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val newReq = chain.request().newBuilder()
                    .addHeader("Authorization", "AccessToken $token")
                    .addHeader("X-User-Authorization", "Basic $authorizationKey")
                    .addHeader("Content-Type", "application/json;charset=UTF-8")
                    .build()
                return chain.proceed(newReq)
            }
        }
    }

    suspend fun getPostalCodesByAddress(address: String): List<String> {
        val data = retry {
            OkHttpUtils.makeAsyncRequest(
                client = OkHttpProvider().httpClient,
                request = Request.Builder()
                    .url("$BASE_URL/postoffice/1.0/by-address?address=$address")
                    .get()
                    .build()
            )
        }!!

        return Gson().fromJson<List<String>>(data.getJSONArray("postoffices").toString())
    }

    suspend fun getDepartmentByPostalCode(postalCode: String): RussianPostDepartment = RussianPostDepartment(
        OkHttpUtils.makeAsyncRequest(
            client = OkHttpProvider().httpClient,
            request = Request.Builder()
                .url("$BASE_URL/postoffice/1.0/$postalCode")
                .get()
                .build()
        )!!
    )

    suspend fun getDepartmentsByAddress(postalCodes: List<String>): List<RussianPostDepartment> {
        val data = retry {
            val departments = arrayListOf<RussianPostDepartment>()

            for (postalCode in postalCodes) {
                departments.add(
                    getDepartmentByPostalCode(postalCode)
                )
            }

            departments
        }.filter { !it.isClosed }

        return data
    }

    suspend fun postOrder(
        deliveryCost: Long,
        amount: Long,
        name: String,
        middleName: String,
        surname: String,
        buildingNumber: String,
        postIndex: String,
        isNal: Boolean,
        weight: Long,
        id: String,
        place: String,
        rawAddress: String,
        region: String,
        street: String,
        phoneNum: Long
    ): String {
        val s = JSONArray(
            listOf(
            mutableMapOf(
                "address-type-to" to "DEFAULT",
                "given-name" to name,
                "house-to" to buildingNumber,
                "index-to" to postIndex,
                "mail-category" to if (isNal) "WITH_DECLARED_VALUE_AND_CASH_ON_DELIVERY" else "WITH_DECLARED_VALUE",
                "mail-direct" to 643,
                "mail-type" to "ONLINE_PARCEL",
                "mass" to weight,
                "middle-name" to "",
                "order-num" to id,
                "place-to" to place,
                "postoffice-code" to "102961",
                "region-to" to region,
                "street-to" to street,
                "surname" to "",
                "tel-address" to phoneNum,
                "fragile" to "false",
                "address-from" to mapOf(
                    "address-type" to "DEFAULT",
                    "index" to "102961"
                ),
                "insr-value" to amount
            ).apply {
                if (isNal) {
                    this["payment"] = amount
                }
            }
            )
        ).toString()

        return OkHttpUtils.makeAsyncRequest(
            client = OkHttpProvider().httpClient,
            request = Request.Builder()
                .url("$BASE_URL/1.0/user/backlog")
                .put(
                    s.toRequestBody(OkHttpUtils.JSON_TYPE)
                )
                .build(),
            s = s
        )!!.getJSONArray("result-ids").getLong(0).toString()
    }

    suspend fun getOrderTrackingNumber(orderId: String): String? {
        return OkHttpUtils.makeAsyncRequest(
            client = OkHttpProvider().httpClient,
            request = Request.Builder()
                .url("$BASE_URL/1.0/backlog/$orderId")
                .get()
                .build(),
            s = "$BASE_URL/1.0/backlog/$orderId"
        )?.getString("barcode")
    }
}
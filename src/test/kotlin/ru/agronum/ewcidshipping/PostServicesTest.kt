package ru.agronum.ewcidshipping

import com.ecwid.apiclient.v3.dto.order.result.FetchedOrder
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.aspectj.lang.annotation.Before
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import ru.agronum.ewcidshipping.api.ecwid.EcwidAPI
import ru.agronum.ewcidshipping.api.ozon.OzonRocketAPI
import ru.agronum.ewcidshipping.api.russianpost.RussianPostAPI
import ru.agronum.ewcidshipping.api.russianpost.RussianPostTariffAPI
import ru.agronum.ewcidshipping.model.ewcid.*
import ru.agronum.ewcidshipping.model.postservice.ozonrocket.OrderLine
import ru.agronum.ewcidshipping.model.postservice.russianpost.RussianPostSendType
import ru.agronum.ewcidshipping.utils.DebugUtils
import java.math.BigDecimal


@SpringBootTest
@AutoConfigureMockMvc
class PostServicesTest @Autowired constructor(
    @Value("\${russian-post.postal-code}")
    val postalCodeFrom: String,
    @Value("\${agronum.store-id}")
    val storeId: String,
    @Value("\${agronum.webhook.authorization}")
    val authorizationHeader: String,
    val russianPostAPI: RussianPostAPI,
    val russianPostTariffAPI: RussianPostTariffAPI,
    val ozonRocketAPI: OzonRocketAPI,
    val ecwidAPI: EcwidAPI,
    val mockMVC: MockMvc
) {
    companion object {
        val TEST_PACKAGES = OzonRocketAPI.calculatePackages(
            weights = listOf(
                OrderLine(
                    "1",
                    "123456",
                    "Bottle",
                    5000,
                    12334.5,
                    quantity = 1,
                ),
                OrderLine(
                    "2",
                    "123457",
                    "Maker",
                    10000,
                    10.5,
                    quantity = 1,
                )
            )
        )
    }


    @Test
    fun getRussianPostDepartmentData() {
        val data = runBlocking {
            russianPostAPI.getDepartmentsByAddress(
                russianPostAPI.getPostalCodesByAddress("dsfds, Уфа, ул. Карла Маркса, д. 12")
            )
        }

        println(data)
    }

    @Test
    fun getOzonDepartmentData() {
        val data = runBlocking {
            ozonRocketAPI.getTariffs(
                packages = TEST_PACKAGES.toMutableList(),
                address = "г. Санкт-Петербург, ул. Русановская, 17/4",
                averagePrice = 1234.0.toBigDecimal()
            )
        }

        println(data)
        println(data.size)
    }

    @Test
    fun calculateRussianPostPackage() {
        val department = runBlocking {
            russianPostAPI.getDepartmentsByAddress(
                russianPostAPI.getPostalCodesByAddress("dsfds, Уфа, ул. Карла Маркса, д. 12")
            )
        }[0]

        val data = runBlocking {
            russianPostTariffAPI.calculateSendTariff(
                postCodeFrom = postalCodeFrom,
                postCodeTo = department.postalCode,
                weight = 1000,
                sum = 123450,
                type = RussianPostSendType.PAID
            )
        }

        println(data)
    }

    @Test
    fun postRussianPostOrder() {
        val department = runBlocking {
            russianPostAPI.getDepartmentsByAddress(
                russianPostAPI.getPostalCodesByAddress("Санкт-Петербург, переулок Пирогова, 18")
            )
        }[0]

        /*val data = runBlocking {
            russianPostTariffAPI.calculateSendTariff(
                postCodeFrom = postalCodeFrom,
                postCodeTo = department.postalCode,
                weight = BigDecimal("12500"),
                sum = BigDecimal("123450"),
                type = RussianPostSendType.NAL_PL
            )
        }

        runBlocking {
            russianPostAPI.postOrder(
                deliveryCost = data.cost.longValueExact(),
                id = "123456",
                amount = 123450,
                rawAddress = "Санкт-Петербург, переулок Пирогова, 18",
                name = "Иван",
                middleName = "Иванович",
                surname = "Иванов",
                phoneNum = 79991234567,
                postIndex = department.postalCode,
                weight = 12500,
                isNal = false,
                buildingNumber = "18",
                region = "Санкт-Петербург",
                street = "переулок Пирогова",
                place = "Санкт-Петербург"
            )
        }*/


        println(department)
    }

    @Test
    fun getOrder() {
        val order = ecwidAPI.getOrder(number = 300590053)
        val shippingOption = order.shippingOption!!
        val address = order.shippingPerson!!

        val packages = OzonRocketAPI.calculatePackages(order.items!!.map { (it.weight!! * 1000).toInt() }.sum())

        DebugUtils.print(order.subtotal.toString())
        DebugUtils.print(packages.sumOf { it.dimensions.weight }.toString())
        println(order.items)
        println(address)
        println(order.paymentMethod)
        println(order.discount)
        println(order.couponDiscount)
    }

    @Test
    fun postRequestToRussianPost() {
        val data = mockMVC.post("/russianpost") {
            this.content = Gson().toJson(OrderRequest(
                storeId = storeId.toLong(),
                cart = Cart(
                    subtotal = 5678.0.toBigDecimal(),
                    shippingAddress = Address(
                        city = "Москва",
                        street = "Салтыковская 41",
                        stateOrProvinceName = "Москва",
                        postalCode = "111672",
                        countryCode = "RU",
                        stateOrProvinceCode = "MSK"
                    ),
                    weight = BigDecimal("1.5"),
                    weightUnit = "kg",
                    couponDiscount = BigDecimal.ZERO,
                    discount = BigDecimal.ZERO,
                    items = listOf(
                        Item(1234.0.toBigDecimal())
                    )
                )
            ))
            this.contentType = MediaType.APPLICATION_JSON
        }.andReturn()

        println(data.response.contentAsString)
    }

    @Test
    fun postRequestToOzon() {
        val data = mockMVC.post("/ozonrocket") {
            this.content = Gson().toJson(OrderRequest(
                storeId = storeId.toLong(),
                cart = Cart(
                    subtotal = 5678.0.toBigDecimal(),
                    shippingAddress = Address(
                        city = "Москва",
                        street = "Салтыковская 41",
                        stateOrProvinceName = "Москва",
                        postalCode = "111672",
                        countryCode = "RU",
                        stateOrProvinceCode = "MSK"
                    ),
                    weight = BigDecimal("1.5"),
                    weightUnit = "kg",
                    couponDiscount = BigDecimal.ZERO,
                    discount = BigDecimal.ZERO,
                    items = listOf(
                        Item(1234.0.toBigDecimal())
                    )
                )
            ))
            this.contentType = MediaType.APPLICATION_JSON
        }.andReturn()

        println(data.response.contentAsString)
    }

    @Test
    fun getRussianPostTrackingNumber() {
        runBlocking {
            println(russianPostAPI.getOrderTrackingNumber("635279950"))
        }
    }

    @Test
    fun postOzonOrder() {
        val data = runBlocking {
            ozonRocketAPI.getTariffs(
                packages = TEST_PACKAGES.toMutableList(),
                address = "Уфа, ул. Карла Маркса, д. 12",
                averagePrice = 1234.0.toBigDecimal()
            )
        }

        println(data)
        println(data.size)
        val f = data.first()

        runBlocking {
            ozonRocketAPI.postOrder(
                userName = "Иван",
                phoneNum = "+79171717111",
                email = "uuuujoyer@yandex.ru",
                rawAddress = f.address,
                ozonDeliveryNumber = f.id.toString(),
                id = "TestOrder#3",
                amount = 12334.5 + 10.5,
                packages = TEST_PACKAGES.toMutableList(),
                deliveryPrice = f.cost.toDouble(),
                needsPayment = true
            )
        }
    }

    @Test
    fun packageCalculating() {
        println(TEST_PACKAGES.toMutableList())
        //println(OzonRocketAPI.calculatePackages(listOf(5800, 5800)))
    }

    @Test
    fun getOzonDepartmentById() {
        runBlocking {
            println(ozonRocketAPI.getTariff("1011000000006527"))
        }
    }

    @Test
    fun discountCalculation() {
        mockMVC.post("/webhook"){
            this.content = Gson().toJson(
                WebhookRequest(
                    eventType = "order.created",
                    entityId = 300590045,
                    data = WebhookRequestData("300590045")
                )
            )
            this.contentType = MediaType.APPLICATION_JSON
            this.header("Authorization", authorizationHeader)
        }.andReturn()
        mockMVC.post("/webhook"){
            this.content = Gson().toJson(
                WebhookRequest(
                    eventType = "order.created",
                    entityId = 300590045,
                    data = WebhookRequestData("300590045")
                )
            )
            this.contentType = MediaType.APPLICATION_JSON
            this.header("Authorization", authorizationHeader)
        }.andReturn()
    }
}

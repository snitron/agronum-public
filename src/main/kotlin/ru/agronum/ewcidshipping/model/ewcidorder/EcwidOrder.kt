package ru.agronum.ewcidshipping.model.ewcidorder

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import ru.agronum.ewcidshipping.model.BaseAuditEntity
import ru.agronum.ewcidshipping.model.postservice.PostServiceType
import java.math.BigDecimal
import javax.persistence.Entity

@Repository
interface EcwidOrderRepository: CrudRepository<EcwidOrder, Long>

@Entity
data class EcwidOrder (
    val ecwidId: Long,
    val weight: BigDecimal,
    val sum: Long? = null,
    val postService: PostServiceType,
    val postServiceOrderId: Long? = null,
    var orderStatus: EcwidOrderStatus
): BaseAuditEntity<Long>()

enum class EcwidOrderStatus {
    CREATED, WAITING_FOR_SHIPPING_SEND, SENT, CANCELLED
}
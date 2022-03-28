package ru.agronum.ewcidshipping.model.ewcidorder

import org.springframework.stereotype.Service

@Service
class EcwidOrderService(
    private val ecwidOrderRepository: EcwidOrderRepository
) {
    fun saveEcwidOrder(order: EcwidOrder): EcwidOrder = ecwidOrderRepository.save(order)
}
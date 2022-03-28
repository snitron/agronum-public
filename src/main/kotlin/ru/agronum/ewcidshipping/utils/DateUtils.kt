package ru.agronum.ewcidshipping.utils

import java.time.Instant
import java.time.ZoneId

object DateUtils {
    private val timeZoneId = ZoneId.of("Europe/Moscow")

    fun getRussianPostTariffDate(): String {
        val date = Instant.now().atZone(timeZoneId)

        return "${date.year}${String.format("%02d", date.monthValue)}${String.format("%02d", date.dayOfMonth)}"
    }

    fun getRussianPostTariffTime(): String {
        val date = Instant.now().atZone(timeZoneId)

        return "${String.format("%2d", date.hour)}${String.format("%2d", date.minute)}"
    }
}
package ru.agronum.ewcidshipping.utils

import org.json.JSONObject

fun <T> JSONObject.getOrNull(key: String): T? {
    return if (this.has(key)) {
         try {
            this.get(key) as T
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }
}

fun JSONObject.getStringOrNull(key: String): String? = this.getOrNull<String>(key)
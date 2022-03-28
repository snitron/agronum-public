package ru.agronum.ewcidshipping.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import ru.gildor.coroutines.okhttp.await

internal inline fun <reified T> Gson.fromJson(json: String) =
    fromJson<T>(json, object : TypeToken<T>() {}.type)

object OkHttpUtils {
    val JSON_TYPE = "application/json".toMediaTypeOrNull()!!

    suspend fun makeAsyncRequest(
        client: OkHttpClient,
        request: Request,
        s: String = ""
    ): JSONObject? {
        val call = client.newCall(request).await()
        val string = withContext(Dispatchers.Default) { call.body!!.string() }
        call.body?.close()
        call.close()

        DebugUtils.print(
            message = "Code: ${call.code}${System.lineSeparator()}$string",
            args = arrayOf(request.url.encodedPath, s)
        )

        return if (call.code == 200) JSONObject(string) else null
    }

    fun makeRequest(client: OkHttpClient, request: Request): JSONObject? {
        val call = client.newCall(request).execute()

        val string = call.body!!.string()
        call.body?.close()
        call.close()

        return if (call.code == 200) JSONObject(string) else null
    }
}
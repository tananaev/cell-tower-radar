package com.tananaev.celltowerradar

import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class CellLocationClient(
    private val client: OkHttpClient = sharedClient,
    private val endpoint: HttpUrl = "https://opencellid.org/cell/get".toHttpUrl(),
    private val apiKey: String = "pk.d47e9b1532bf663adf3b3fd443f2b3e6",
) {
    suspend fun getCellLocation(tower: CellTower): LookupResult {
        if (!tower.isValid) return LookupResult.Unavailable
        val url = endpoint.newBuilder()
            .addQueryParameter("key", apiKey)
            .addQueryParameter("mcc", tower.mcc.toString())
            .addQueryParameter("mnc", tower.mnc.toString())
            .addQueryParameter("lac", tower.area.toString())
            .addQueryParameter("cellid", tower.id.toString())
            .addQueryParameter("radio", tower.radio)
            .addQueryParameter("format", "json")
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        try {
                            if (it.isSuccessful) parseResponse(it.body.string()) else LookupResult.Unavailable
                        } catch (_: IOException) {
                            LookupResult.Unavailable
                        }
                    }
                    continuation.resume(result)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(LookupResult.Unavailable)
                }
            })
        }
    }

    internal fun parseResponse(body: String): LookupResult = try {
        val json = JsonParser.parseString(body).asJsonObject
        when {
            json.has("error") -> if (json.get("code")?.asInt == 1 &&
                json.get("error")?.asString == "Cell not found") {
                LookupResult.NotFound
            } else {
                LookupResult.Unavailable
            }
            else -> {
                val lat = json.get("lat")?.asDouble
                val lon = json.get("lon")?.asDouble
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    LookupResult.Found(TowerLocation(lat, lon))
                } else {
                    LookupResult.Unavailable
                }
            }
        }
    } catch (_: RuntimeException) {
        // Invalid JSON, null fields, non-numeric values and error pages are recoverable.
        LookupResult.Unavailable
    }

    companion object {
        private val sharedClient = OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

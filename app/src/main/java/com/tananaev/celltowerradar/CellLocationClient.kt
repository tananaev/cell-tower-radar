package com.tananaev.celltowerradar

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import java.io.IOException

class CellLocationClient {

    class CellTowerRequest(cellTower: CellTower) {
        val cellTowers: Array<CellTower> = arrayOf(cellTower)
    }

    class CellTower {
        var radioType: String? = null
        var mobileCountryCode = 0
        var mobileNetworkCode = 0
        var locationAreaCode = 0
        var cellId = 0
    }

    private val gson = Gson()
    private val okHttpClient = OkHttpClient()

    interface CellLocationCallback {
        fun onSuccess(lat: Double, lon: Double)
        fun onFailure()
    }

    fun getCellLocation(cellTower: CellTower, callback: CellLocationCallback) {
        val request = Request.Builder()
                .url(URL)
                .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(CellTowerRequest(cellTower))))
                .build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val result = JsonParser.parseString(response.body()?.string())
                if (result.isJsonObject) {
                    val json = result as JsonObject
                    if (json.has("location")) {
                        val location = json.getAsJsonObject("location")
                        callback.onSuccess(
                            location.getAsJsonPrimitive("lat").asDouble,
                            location.getAsJsonPrimitive("lng").asDouble)
                        return
                    }
                }
                callback.onFailure()
            }

            override fun onFailure(call: Call, e: IOException) {
                callback.onFailure()
            }
        })
    }

    companion object {
        private const val URL = "https://location.services.mozilla.com/v1/geolocate?key=test"
    }
}

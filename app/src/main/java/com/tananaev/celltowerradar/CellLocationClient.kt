package com.tananaev.celltowerradar

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import java.io.IOException

class CellLocationClient {

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
        val key = "pk.d47e9b1532bf663adf3b3fd443f2b3e6"
        val mcc = cellTower.mobileCountryCode
        val mnc = cellTower.mobileNetworkCode
        val lac = cellTower.locationAreaCode
        val cid = cellTower.cellId
        val url = "https://opencellid.org/cell/get?key=${key}&mcc=${mcc}&mnc=${mnc}&lac=${lac}&cellid=${cid}&format=json"
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val result = JsonParser.parseString(response.body()?.string())
                if (result.isJsonObject) {
                    val json = result as JsonObject
                    callback.onSuccess(
                        json.getAsJsonPrimitive("lat").asDouble,
                        json.getAsJsonPrimitive("lon").asDouble
                    )
                    return
                }
                callback.onFailure()
            }

            override fun onFailure(call: Call, e: IOException) {
                callback.onFailure()
            }
        })
    }
}

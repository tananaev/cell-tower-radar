package com.tananaev.celltowerradar

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class CellInfoReader(context: Context) {
    private val manager = context.getSystemService(TelephonyManager::class.java)
    private val executor = ContextCompat.getMainExecutor(context)

    @SuppressLint("MissingPermission")
    suspend fun read(): List<CellTower> {
        val telephony = manager ?: return emptyList()
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android may throttle fresh requests. Bound the wait and fall back to its cache.
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { continuation ->
                    telephony.requestCellInfoUpdate(executor, object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                            if (continuation.isActive) continuation.resume(cellInfo)
                        }

                        override fun onError(errorCode: Int, detail: Throwable?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                }
            } ?: withContext(Dispatchers.IO) { telephony.allCellInfo }
        } else {
            withContext(Dispatchers.IO) { telephony.allCellInfo }
        }
        return info.orEmpty().mapNotNull(::toTower).filter { it.isValid }.distinct()
    }

    @Suppress("DEPRECATION") // Numeric MCC/MNC access is necessary on Android 6 and 7.
    private fun toTower(info: CellInfo): CellTower? = when {
        info is CellInfoGsm -> info.cellIdentity.let {
            CellTower("GSM", if (Build.VERSION.SDK_INT >= 28) it.mccString?.toIntOrNull() ?: -1 else it.mcc,
                if (Build.VERSION.SDK_INT >= 28) it.mncString?.toIntOrNull() ?: -1 else it.mnc,
                it.lac, it.cid.toLong())
        }
        info is CellInfoLte -> info.cellIdentity.let {
            CellTower("LTE", if (Build.VERSION.SDK_INT >= 28) it.mccString?.toIntOrNull() ?: -1 else it.mcc,
                if (Build.VERSION.SDK_INT >= 28) it.mncString?.toIntOrNull() ?: -1 else it.mnc,
                it.tac, it.ci.toLong())
        }
        info is CellInfoWcdma -> info.cellIdentity.let {
            CellTower("UMTS", if (Build.VERSION.SDK_INT >= 28) it.mccString?.toIntOrNull() ?: -1 else it.mcc,
                if (Build.VERSION.SDK_INT >= 28) it.mncString?.toIntOrNull() ?: -1 else it.mnc,
                it.lac, it.cid.toLong())
        }
        Build.VERSION.SDK_INT >= 29 && info is CellInfoNr -> (info.cellIdentity as CellIdentityNr).let {
            CellTower("NR", it.mccString?.toIntOrNull() ?: -1, it.mncString?.toIntOrNull() ?: -1,
                it.tac, it.nci)
        }
        else -> null
    }
}

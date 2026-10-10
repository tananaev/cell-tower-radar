package com.tananaev.celltowerradar

/** Cell IDs are only unique within a radio technology, network and area. */
data class CellTower(
    val radio: String,
    val mcc: Int,
    val mnc: Int,
    val area: Int,
    val id: Long,
) {
    val isValid: Boolean
        get() = mcc in 1..999 && mnc in 0..999 && area in 0 until Int.MAX_VALUE &&
            id >= 0 && id != Int.MAX_VALUE.toLong() && id != Long.MAX_VALUE
}

data class TowerLocation(val latitude: Double, val longitude: Double)

sealed interface LookupResult {
    data class Found(val location: TowerLocation) : LookupResult
    data object NotFound : LookupResult
    data object Unavailable : LookupResult
}

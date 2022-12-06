package com.tananaev.celltowerradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.*
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.tananaev.celltowerradar.CellLocationClient.*

class MainFragment : SupportMapFragment(), OnMapReadyCallback {

    val handler = Handler(Looper.getMainLooper())
    private lateinit var map: GoogleMap
    private val cellLocationClient = CellLocationClient()
    private val cells: MutableMap<Int, Marker?> = HashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().window.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                statusBarColor = Color.TRANSPARENT
            }
        }
        getMapAsync(this)
        RatingDialogFragment.showRating(requireContext(), childFragmentManager)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.style_json))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view?.setOnApplyWindowInsetsListener { _, insets ->
                map.setPadding(0, insets.systemWindowInsetTop, 0, 0)
                insets
            }
        }
        var locationInitialized = false
        map.setOnMyLocationChangeListener { location ->
            if (!locationInitialized) {
                val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude), 10f
                )
                map.moveCamera(cameraUpdate)
                locationInitialized = true
            }
        }
        map.setInfoWindowAdapter(object : InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? {
                return null
            }

            override fun getInfoContents(marker: Marker): View {
                val title = TextView(requireContext())
                title.text = marker.title
                return title
            }
        })
        checkPermissions()
    }

    private fun checkPermissions() {
        val requiredPermission: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (ContextCompat.checkSelfPermission(requireContext(), requiredPermission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), requiredPermission)) {
                // TODO show dialog
            } else {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(requiredPermission), 0)
            }
        } else {
            loadData()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadData()
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadData() {
        map.isMyLocationEnabled = true
        handler.post(object : Runnable {
            override fun run() {
                loadCellInfo()
                handler.postDelayed(this, REFRESH_DELAY.toLong())
            }
        })
    }

    private fun addCellTower(mcc: Int, mnc: Int, lac: Int, cid: Int, lat: Double, lon: Double) {
        cells[cid] = map.addMarker(MarkerOptions().position(LatLng(lat, lon))
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.tower))
                .title("Country Code: $mcc\nNetwork Code: $mnc\nLocation Area Code: $lac\nCell ID: $cid"))
    }

    @SuppressLint("MissingPermission")
    private fun loadCellInfo() {
        val telephonyManager = requireContext().getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val cellList = telephonyManager.allCellInfo
        if (cellList != null) {
            for (cell in cellList) {
                val cellTower = CellTower()
                when (cell) {
                    is CellInfoGsm -> {
                        cellTower.radioType = "gsm"
                        cellTower.mobileCountryCode = cell.cellIdentity.mcc
                        cellTower.mobileNetworkCode = cell.cellIdentity.mnc
                        cellTower.locationAreaCode = cell.cellIdentity.lac
                        cellTower.cellId = cell.cellIdentity.cid
                    }
                    is CellInfoLte -> {
                        cellTower.radioType = "lte"
                        cellTower.mobileCountryCode = cell.cellIdentity.mcc
                        cellTower.mobileNetworkCode = cell.cellIdentity.mnc
                        cellTower.locationAreaCode = cell.cellIdentity.tac
                        cellTower.cellId = cell.cellIdentity.ci
                    }
                    is CellInfoWcdma -> {
                        cellTower.radioType = "wcdma"
                        cellTower.mobileCountryCode = cell.cellIdentity.mcc
                        cellTower.mobileNetworkCode = cell.cellIdentity.mnc
                        cellTower.locationAreaCode = cell.cellIdentity.lac
                        cellTower.cellId = cell.cellIdentity.cid
                    }
                }
                if (cellTower.cellId != 0 && cellTower.cellId != Int.MAX_VALUE && !cells.containsKey(cellTower.cellId)) {
                    cellLocationClient.getCellLocation(cellTower, object : CellLocationCallback {
                        override fun onSuccess(lat: Double, lon: Double) {
                            handler.post {
                                addCellTower(
                                    cellTower.mobileCountryCode, cellTower.mobileNetworkCode,
                                    cellTower.locationAreaCode, cellTower.cellId, lat, lon)
                            }
                        }

                        override fun onFailure() {}
                    })
                }
            }
        }
    }

    companion object {
        private const val REFRESH_DELAY = 60 * 1000
    }
}

package com.tananaev.celltowerradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.tananaev.celltowerradar.databinding.FragmentMainBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class MainFragment : Fragment(R.layout.fragment_main) {
    private var binding: FragmentMainBinding? = null
    private var map: GoogleMap? = null
    private var locationInitialized = false
    private val refreshRequests = MutableStateFlow(0)
    private val client = CellLocationClient()
    private val markers = mutableMapOf<CellTower, Marker>()
    private val cache = object : LinkedHashMap<CellTower, LookupResult>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CellTower, LookupResult>?) = size > 128
    }
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshRequests.value++
    }
    private val preferences by lazy {
        requireContext().getSharedPreferences("permissions", android.content.Context.MODE_PRIVATE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentMainBinding.bind(view)
        locationInitialized = savedInstanceState?.getBoolean("locationInitialized") ?: false
        binding?.attribution?.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        val fragment = childFragmentManager.findFragmentById(R.id.map_view) as SupportMapFragment
        fragment.getMapAsync { googleMap ->
            if (binding == null) return@getMapAsync
            map = googleMap
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.style_json))
            // Keep Google's attribution above our data-source credit.
            googleMap.setPadding(0, 0, 0, (24 * resources.displayMetrics.density).toInt())
            googleMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
                override fun getInfoWindow(marker: Marker): View? = null
                override fun getInfoContents(marker: Marker): View = android.widget.TextView(requireContext()).apply {
                    text = marker.title
                    textSize = 16f
                    setPadding(16, 8, 16, 8)
                }
            })
            refreshRequests.value++
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                refreshRequests.collectLatest {
                    if (map == null) return@collectLatest
                    if (!hasPermission()) {
                        showPermissionRequired()
                        return@collectLatest
                    }
                    val locationManager = requireContext().getSystemService(LocationManager::class.java)
                    if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
                        showStatus(getString(R.string.location_disabled), R.string.location_settings) {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                        return@collectLatest
                    }
                    try {
                        enableLocation()
                        coroutineScope {
                            launch { centerOnLocation(locationManager) }
                            while (isActive) {
                                refreshTowers()
                                delay(60_000)
                            }
                        }
                    } catch (_: SecurityException) {
                        showPermissionRequired()
                    } catch (_: UnsupportedOperationException) {
                        showStatus(getString(R.string.cell_info_unavailable), R.string.retry, action = ::retry)
                    }
                }
            }
        }
    }

    private fun retry() {
        cache.entries.removeAll { it.value is LookupResult.NotFound }
        refreshRequests.value++
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionRequired() {
        val openSettings = preferences.getBoolean("requested", false) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        showStatus(getString(R.string.permission_required),
            if (openSettings) R.string.app_settings else R.string.grant_permission) {
            if (openSettings) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${requireContext().packageName}".toUri()))
            } else {
                preferences.edit { putBoolean("requested", true) }
                permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocation() { map?.isMyLocationEnabled = true }

    @SuppressLint("MissingPermission")
    private suspend fun centerOnLocation(manager: LocationManager) {
        if (locationInitialized) return
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            if (!manager.isProviderEnabled(provider)) continue
            val location = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val cancellation = CancellationSignal()
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    LocationManagerCompat.getCurrentLocation(manager, provider, cancellation,
                        ContextCompat.getMainExecutor(requireContext())) {
                        if (continuation.isActive) continuation.resume(it)
                    }
                }
            } ?: continue
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 14f))
            locationInitialized = true
            break
        }
    }

    private suspend fun refreshTowers() {
        if (markers.isEmpty()) showStatus(getString(R.string.loading_cells))
        val towers = CellInfoReader(requireContext()).read()
        markers.keys.filter { it !in towers }.forEach { markers.remove(it)?.remove() }
        if (towers.isEmpty()) {
            showStatus(getString(R.string.no_cells), R.string.retry, action = ::retry)
            return
        }
        val results = withTimeoutOrNull(30_000) {
            coroutineScope {
                val limit = Semaphore(4)
                towers.map { tower ->
                    async {
                        tower to (cache[tower] ?: limit.withPermit { client.getCellLocation(tower) }.also {
                            if (it !is LookupResult.Unavailable) cache[tower] = it
                        })
                    }
                }.awaitAll()
            }
        }
        if (results == null) {
            showStatus(getString(R.string.lookup_unavailable), R.string.retry, action = ::retry)
            return
        }
        results.forEach { (tower, result) ->
            if (result is LookupResult.Found && tower !in markers) {
                map?.addMarker(MarkerOptions()
                    .position(LatLng(result.location.latitude, result.location.longitude))
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.tower))
                    .title(getString(R.string.tower_details, tower.radio, tower.mcc, tower.mnc, tower.area, tower.id)))
                    ?.let { markers[tower] = it }
            }
        }
        val failed = results.any { it.second is LookupResult.Unavailable }
        when {
            markers.isNotEmpty() -> {
                val count = resources.getQuantityString(R.plurals.towers_found, markers.size, markers.size)
                showStatus(if (failed) getString(R.string.partial_results, count) else count,
                    R.string.refresh, contentAvailable = true, action = ::retry)
            }
            failed -> showStatus(getString(R.string.lookup_unavailable), R.string.retry, action = ::retry)
            else -> showStatus(getString(R.string.no_tower_locations), R.string.retry, action = ::retry)
        }
    }

    private fun showStatus(text: String, actionLabel: Int? = null, contentAvailable: Boolean = false,
                           action: (() -> Unit)? = null) {
        binding?.apply {
            statusView.text = text
            statusAction.isVisible = actionLabel != null
            actionLabel?.let(statusAction::setText)
            statusAction.setOnClickListener { action?.invoke() }
        }
        parentFragmentManager.setFragmentResult(CONTENT_STATE, Bundle().apply { putBoolean(CONTENT_AVAILABLE, contentAvailable) })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("locationInitialized", locationInitialized)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        map?.setInfoWindowAdapter(null)
        map = null
        markers.clear()
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val CONTENT_STATE = "towerContentState"
        const val CONTENT_AVAILABLE = "contentAvailable"
    }
}

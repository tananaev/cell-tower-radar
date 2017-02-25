package com.tananaev.celltowerradar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.HashMap;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap map;
    private CellLocationClient cellLocationClient = new CellLocationClient();
    private Map<Integer, Marker> cells = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.style_json));

        map.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
            @Override
            public void onMyLocationChange(Location location) {
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                        new LatLng(location.getLatitude(), location.getLongitude()), 12);
                map.moveCamera(cameraUpdate);
            }
        });

        checkPermissions();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                // TODO show dialog
            } else {
                ActivityCompat.requestPermissions(this, new String[]{ android.Manifest.permission.ACCESS_COARSE_LOCATION }, 0);
            }
        } else {
            loadData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadData();
        } else {
            // TODO show error
        }
    }

    @SuppressWarnings("MissingPermission")
    private void loadData() {
        map.setMyLocationEnabled(true);
        loadCellInfo();
    }

    private void addCellTower(int id, double lat, double lon) {
        cells.put(id, map.addMarker(new MarkerOptions().position(new LatLng(lat, lon))));
    }

    @SuppressWarnings("MissingPermission")
    private void loadLocationInfo() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                map.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())));
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        }, Looper.getMainLooper());
    }

    @SuppressWarnings("NewApi")
    private void loadCellInfo() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        for (final CellInfo cell : telephonyManager.getAllCellInfo()) {

            final CellLocationClient.CellTower cellTower = new CellLocationClient.CellTower();

            if (cell instanceof CellInfoGsm) {
                CellInfoGsm cellInfoGsm = (CellInfoGsm) cell;
                cellTower.setRadioType("gsm");
                cellTower.setMobileCountryCode(cellInfoGsm.getCellIdentity().getMcc());
                cellTower.setMobileNetworkCode(cellInfoGsm.getCellIdentity().getMnc());
                cellTower.setLocationAreaCode(cellInfoGsm.getCellIdentity().getLac());
                cellTower.setCellId(cellInfoGsm.getCellIdentity().getCid());
            } else if (cell instanceof CellInfoLte) {
                CellInfoLte cellInfoLte = (CellInfoLte) cell;
                cellTower.setRadioType("lte");
                cellTower.setMobileCountryCode(cellInfoLte.getCellIdentity().getMcc());
                cellTower.setMobileNetworkCode(cellInfoLte.getCellIdentity().getMnc());
                cellTower.setLocationAreaCode(cellInfoLte.getCellIdentity().getTac());
                cellTower.setCellId(cellInfoLte.getCellIdentity().getCi());
            } else if (cell instanceof CellInfoWcdma) {
                CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) cell;
                cellTower.setRadioType("wcdma");
                cellTower.setMobileCountryCode(cellInfoWcdma.getCellIdentity().getMcc());
                cellTower.setMobileNetworkCode(cellInfoWcdma.getCellIdentity().getMnc());
                cellTower.setLocationAreaCode(cellInfoWcdma.getCellIdentity().getLac());
                cellTower.setCellId(cellInfoWcdma.getCellIdentity().getCid());
            }

            if (cellTower.getCellId() != 0 && cellTower.getCellId() != Integer.MAX_VALUE && !cells.containsKey(cellTower.getCellId())) {
                cellLocationClient.getCellLocation(cellTower, new CellLocationClient.CellLocationCallback() {
                    @Override
                    public void onSuccess(final double lat, final double lon) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                addCellTower(cellTower.getCellId(), lat, lon);
                            }
                        });
                    }

                    @Override
                    public void onFailure() {
                    }
                });
            }

        }
    }

}

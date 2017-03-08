package com.tananaev.celltowerradar;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int REFRESH_DELAY = 60 * 1000;

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

        map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                TextView title = new TextView(MapsActivity.this);
                title.setText(marker.getTitle());
                return title;
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
        final Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                loadCellInfo();
                handler.postDelayed(this, REFRESH_DELAY);
            }
        });
    }

    private void addCellTower(int mcc, int mnc, int lac, int cid, double lat, double lon) {
        cells.put(cid, map.addMarker(new MarkerOptions().position(new LatLng(lat, lon))
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.tower))
                .title("MCC: " + mcc + "\nMNC: " + mnc + "\nLAC: " + lac + "\nCID: " + cid)));
    }

    @SuppressWarnings("NewApi")
    private void loadCellInfo() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        List<CellInfo> cellList = telephonyManager.getAllCellInfo();

        if (cellList != null) {
            for (final CellInfo cell : cellList) {

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
                                    addCellTower(
                                            cellTower.getMobileCountryCode(), cellTower.getMobileNetworkCode(),
                                            cellTower.getLocationAreaCode(), cellTower.getCellId(), lat, lon);
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

}

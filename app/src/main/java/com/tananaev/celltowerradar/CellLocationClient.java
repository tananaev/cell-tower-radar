package com.tananaev.celltowerradar;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CellLocationClient {

    private static final String URL = "https://location.services.mozilla.com/v1/geolocate?key=test";

    public static class CellTowerRequest {

        private CellTower[] cellTowers;

        public CellTowerRequest(CellTower cellTower) {
            cellTowers = new CellTower[] { cellTower };
        }

        public CellTower[] getCellTowers() {
            return cellTowers;
        }

    }

    public static class CellTower {

        private String radioType;
        private int mobileCountryCode;
        private int mobileNetworkCode;
        private int locationAreaCode;
        private int cellId;

        public String getRadioType() {
            return radioType;
        }

        public void setRadioType(String radioType) {
            this.radioType = radioType;
        }

        public int getMobileCountryCode() {
            return mobileCountryCode;
        }

        public void setMobileCountryCode(int mobileCountryCode) {
            this.mobileCountryCode = mobileCountryCode;
        }

        public int getMobileNetworkCode() {
            return mobileNetworkCode;
        }

        public void setMobileNetworkCode(int mobileNetworkCode) {
            this.mobileNetworkCode = mobileNetworkCode;
        }

        public int getLocationAreaCode() {
            return locationAreaCode;
        }

        public void setLocationAreaCode(int locationAreaCode) {
            this.locationAreaCode = locationAreaCode;
        }

        public int getCellId() {
            return cellId;
        }

        public void setCellId(int cellId) {
            this.cellId = cellId;
        }

    }

    private Gson gson = new Gson();
    private JsonParser jsonParser = new JsonParser();
    private OkHttpClient okHttpClient = new OkHttpClient();

    public interface CellLocationCallback {
        void onSuccess(double lat, double lon);
        void onFailure();
    }

    public void getCellLocation(CellTower cellTower, final CellLocationCallback callback) {
        final Request request = new Request.Builder()
                .url(URL)
                .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(new CellTowerRequest(cellTower))))
                .build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                JsonObject json = (JsonObject) jsonParser.parse(response.body().charStream());
                if (json.has("location")) {
                    JsonObject location = json.getAsJsonObject("location");
                    callback.onSuccess(
                            location.getAsJsonPrimitive("lat").getAsDouble(),
                            location.getAsJsonPrimitive("lng").getAsDouble());
                    return;
                }
                callback.onFailure();
            }

            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure();
            }
        });
    }

}

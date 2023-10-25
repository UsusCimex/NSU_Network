package ru.nsu;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import ru.nsu.geocode.GeoData;
import ru.nsu.geocode.Hit;
import ru.nsu.geocode.Point;
import ru.nsu.opentrip.Feature;
import ru.nsu.opentrip.FeatureData;
import ru.nsu.opentrip.Properties;
import ru.nsu.opentripinfo.FeatureInfoData;
import ru.nsu.opentripinfo.WikipediaExtracts;
import ru.nsu.openweather.WeatherData;

public class APIWorker {
    private static final String OPENWEATHER_API_KEY = "8a7a08eb796672a72dc63b9f3930fe8c";
    private static final String OPENTRIP_API_KEY = "5ae2e3f221c38a28845f05b6d2d32114e33e4b62b7b95599fefb0a6f";
    private static final String GEOCODE_API_KEY = "04a9c8ba-db4f-4131-82d5-8c0d8d22bbc5";

    private static final String GEOCODE_API_URL = "https://graphhopper.com/api/1/geocode?q={ADDRESS}&locale=ru&key={API_KEY}";
    private static final String OPENWEATHER_API_URL = "https://api.openweathermap.org/data/2.5/weather?lat={LAT}&lon={LON}&appid={API_KEY}";
    private static final String OPENTRIP_API_URL = "https://api.opentripmap.com/0.1/ru/places/bbox?lon_min={LON_MIN}&lat_min={LAT_MIN}&lon_max={LON_MAX}&lat_max={LAT_MAX}&format=geojson&apikey={API_KEY}";
    private static final String OPENTRIP_INFO_API_URL = "https://api.opentripmap.com/0.1/ru/places/xid/{XID}?apikey={API_KEY}";

    private OkHttpClient client;
    public APIWorker(OkHttpClient client) {
        this.client = client;
    }

    // Метод для выполнения HTTP GET-запросов
    private void sendGetRequest(String url, Callback callback) {
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(callback);
    }

    // Метод для получения локаций по названию
    public void getLocationsByAddress(String address, Callback callback) {
        try {
            String apiUrl = GEOCODE_API_URL
                    .replace("{ADDRESS}", URLEncoder.encode(address, StandardCharsets.UTF_8))
                    .replace("{API_KEY}", GEOCODE_API_KEY);
            System.out.println("GEOCODE_URL: " + apiUrl);
            sendGetRequest(apiUrl, callback);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    // Метод для получения погоды по координатам
    public void getWeatherByCoordinates(double lat, double lon, Callback callback) {
        try {
            String apiUrl = OPENWEATHER_API_URL
                    .replace("{LAT}", String.valueOf(lat))
                    .replace("{LON}", String.valueOf(lon))
                    .replace("{API_KEY}", OPENWEATHER_API_KEY);
            System.out.println("OPENWEATHER_URL: " + apiUrl);
            sendGetRequest(apiUrl, callback);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Метод для получения списка интересных мест по координатам
    public void getInterestingPlacesByCoordinates(double lonMin, double latMin, double lonMax, double latMax, Callback callback) {
        try {
            String apiUrl = OPENTRIP_API_URL
                    .replace("{LON_MIN}", String.valueOf(lonMin))
                    .replace("{LAT_MIN}", String.valueOf(latMin))
                    .replace("{LON_MAX}", String.valueOf(lonMax))
                    .replace("{LAT_MAX}", String.valueOf(latMax))
                    .replace("{API_KEY}", OPENTRIP_API_KEY);
            System.out.println("OPENTRIP_URL: " + apiUrl);
            sendGetRequest(apiUrl, callback);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void getInfoAboutPlace(String XId, Callback callback) {
        try {
            String apiUrl = OPENTRIP_INFO_API_URL
                    .replace("{XID}", XId)
                    .replace("{API_KEY}", OPENTRIP_API_KEY);
            System.out.println("OPENTRIP_INFO_URL: " + apiUrl);
            sendGetRequest(apiUrl, callback);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

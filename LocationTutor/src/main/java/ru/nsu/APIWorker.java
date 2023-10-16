package ru.nsu;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    // Метод для выполнения HTTP GET-запросов
    private String sendGetRequest(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));

            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return response.toString();
        } else {
            throw new Exception("HTTP GET request failed with response code " + responseCode);
        }
    }

    // Метод для получения локаций по названию
    public List<Location> getLocationsByAddress(String address) {
        List<Location> locations = new ArrayList<>();
        try {
            String apiUrl = GEOCODE_API_URL
                    .replace("{ADDRESS}", URLEncoder.encode(address, StandardCharsets.UTF_8))
                    .replace("{API_KEY}", GEOCODE_API_KEY);
            System.out.println("GEOCODE_URL: " + apiUrl);

            String response = sendGetRequest(apiUrl);
            ObjectMapper objectMapper = new ObjectMapper();
            // Чтение JSON из файла и преобразование в объект
            GeoData geoData = objectMapper.readValue(response, GeoData.class);

            // Обращение к полям объекта geoData
            List<Hit> hits = geoData.getHits();
            for (Hit hit : hits) {
                String name = hit.getName();
                String state = hit.getState();
                String country = hit.getCountry();
                String city = hit.getCity();
                Point point = hit.getPoint();
                double lat = point.getLat();
                double lng = point.getLng();

                locations.add(new Location(lat, lng, name + ", " + city + ", " + state + ", " + country));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return locations;
    }


    // Метод для получения погоды по координатам
    public String getWeatherByCoordinates(double lat, double lon) {
        String weather = null;
        try {
            String apiUrl = OPENWEATHER_API_URL
                    .replace("{LAT}", String.valueOf(lat))
                    .replace("{LON}", String.valueOf(lon))
                    .replace("{API_KEY}", OPENWEATHER_API_KEY);
            System.out.println("OPENWEATHER_URL: " + apiUrl);

            String response = sendGetRequest(apiUrl);
            ObjectMapper objectMapper = new ObjectMapper();
            // Чтение JSON из файла и преобразование в объект
            WeatherData weatherData = objectMapper.readValue(response, WeatherData.class);
            String main = weatherData.getWeather().get(0).getMain();
            String description = weatherData.getWeather().get(0).getDescription();
            weather = main + "(" + description + ")";
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return weather;
    }

    // Метод для получения списка интересных мест по координатам
    public List<Properties> getInterestingPlacesByCoordinates(double lonMin, double latMin, double lonMax, double latMax) {
        List<Properties> features = new ArrayList<>();
        try {
            String apiUrl = OPENTRIP_API_URL
                    .replace("{LON_MIN}", String.valueOf(lonMin))
                    .replace("{LAT_MIN}", String.valueOf(latMin))
                    .replace("{LON_MAX}", String.valueOf(lonMax))
                    .replace("{LAT_MAX}", String.valueOf(latMax))
                    .replace("{API_KEY}", OPENTRIP_API_KEY);
            System.out.println("OPENTRIP_URL: " + apiUrl);

            String response = sendGetRequest(apiUrl);
            ObjectMapper objectMapper = new ObjectMapper();
            // Чтение JSON из файла и преобразование в объект
            FeatureData featureData = objectMapper.readValue(response, FeatureData.class);
            for (Feature feature : featureData.getFeatures()) {
                features.add(feature.getProperties());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return features;
    }

    public String getInfoAboutPlace(String XId) {
        String description = null;
        try {
            String apiUrl = OPENTRIP_INFO_API_URL
                    .replace("{XID}", XId)
                    .replace("{API_KEY}", OPENTRIP_API_KEY);
            System.out.println("OPENTRIP_INFO_URL: " + apiUrl);

            String response = sendGetRequest(apiUrl);
            ObjectMapper objectMapper = new ObjectMapper();
            FeatureInfoData featureInfoData = objectMapper.readValue(response, FeatureInfoData.class);
            WikipediaExtracts wikipediaExtracts = featureInfoData.getWikipedia_extracts();
            if (wikipediaExtracts != null) {
                description = wikipediaExtracts.getText();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return description;
    }
}

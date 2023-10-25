package ru.nsu.geocode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.nsu.Location;

import java.util.ArrayList;
import java.util.List;

public class GeoData {
    private List<Hit> hits;
    private String locale;

    public static List<Location> parseJSON(String str) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        // Чтение JSON из файла и преобразование в объект
        GeoData geoData = objectMapper.readValue(str, GeoData.class);
        List<Location> locations = new ArrayList<>();

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
        return locations;
    }

    public List<Hit> getHits() {
        return hits;
    }

    public void setHits(List<Hit> hits) {
        this.hits = hits;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}

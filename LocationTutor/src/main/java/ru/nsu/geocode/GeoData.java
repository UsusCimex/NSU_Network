package ru.nsu.geocode;

import java.util.List;

public class GeoData {
    private List<Hit> hits;
    private String locale;

    // Геттеры и сеттеры

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

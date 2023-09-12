package ru.nsu;

public class Location {
    private double lat;
    private double lon;
    private String name;
    private String weather;
    private String InterestingPlaces;

    public Location(double lat, double lon, String name) {
        this.lat = lat;
        this.lon = lon;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public String getInterestingPlaces() {
        return InterestingPlaces;
    }

    public String getWeather() {
        return weather;
    }

    public void setInterestingPlaces(String interestingPlaces) {
        InterestingPlaces = interestingPlaces;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }
}
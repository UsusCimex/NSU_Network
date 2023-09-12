package ru.nsu.geocode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Hit {
    private Point point;
    private List<Double> extent;
    private String name;
    private String country;
    private String city;
    private String countrycode;
    private String state;
    private String postcode;
    private long osm_id;
    private String osm_type;
    private String osm_key;
    private String osm_value;

    // Геттеры и сеттеры

    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }

    public List<Double> getExtent() {
        return extent;
    }

    public void setExtent(List<Double> extent) {
        this.extent = extent;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCountrycode() {
        return countrycode;
    }

    public void setCountrycode(String countrycode) {
        this.countrycode = countrycode;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public long getOsm_id() {
        return osm_id;
    }

    public void setOsm_id(long osm_id) {
        this.osm_id = osm_id;
    }

    public String getOsm_type() {
        return osm_type;
    }

    public void setOsm_type(String osm_type) {
        this.osm_type = osm_type;
    }

    public String getOsm_key() {
        return osm_key;
    }

    public void setOsm_key(String osm_key) {
        this.osm_key = osm_key;
    }

    public String getOsm_value() {
        return osm_value;
    }

    public void setOsm_value(String osm_value) {
        this.osm_value = osm_value;
    }
}

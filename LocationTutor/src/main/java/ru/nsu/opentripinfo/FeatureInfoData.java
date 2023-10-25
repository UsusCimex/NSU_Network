package ru.nsu.opentripinfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureInfoData {
    private String xid;
    private String name;
    private Address address;
    private String rate;
    private String osm;
    private String kinds;
    private Sources sources;
    private String otm;
    private PlaceInfo placeInfo;
    private String image;
    private Preview preview;
    private Point point;
    private WikipediaExtracts wikipedia_extracts;

    public static String parseJSON(String str) throws JsonProcessingException {
        String res = null;
        ObjectMapper objectMapper = new ObjectMapper();
        FeatureInfoData featureInfoData = objectMapper.readValue(str, FeatureInfoData.class);
        if (featureInfoData.name != null && !featureInfoData.name.isEmpty()) {
            res = "- " + featureInfoData.name;
        }
        WikipediaExtracts wikipediaExtracts = featureInfoData.getWikipedia_extracts();
        if (wikipediaExtracts != null) {
            res += "\n-- " + wikipediaExtracts.getText();
        }
        return res;
    }

    public WikipediaExtracts getWikipedia_extracts() {
        return wikipedia_extracts;
    }

    public void setWikipedia_extracts(WikipediaExtracts wikipedia_extracts) {
        this.wikipedia_extracts = wikipedia_extracts;
    }

    public PlaceInfo getPlaceInfo() {
        return placeInfo;
    }

    public void setPlaceInfo(PlaceInfo placeInfo) {
        this.placeInfo = placeInfo;
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public String getRate() {
        return rate;
    }

    public void setRate(String rate) {
        this.rate = rate;
    }

    public String getOsm() {
        return osm;
    }

    public void setOsm(String osm) {
        this.osm = osm;
    }

    public String getKinds() {
        return kinds;
    }

    public void setKinds(String kinds) {
        this.kinds = kinds;
    }

    public Sources getSources() {
        return sources;
    }

    public void setSources(Sources sources) {
        this.sources = sources;
    }

    public String getOtm() {
        return otm;
    }

    public void setOtm(String otm) {
        this.otm = otm;
    }

    public PlaceInfo getInfo() {
        return placeInfo;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Preview getPreview() {
        return preview;
    }

    public void setPreview(Preview preview) {
        this.preview = preview;
    }

    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }
}

package ru.nsu.opentrip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class FeatureData {
    private String type;
    private List<Feature> features;

    public static List<Properties> parseJSON(String str) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        FeatureData featureData = objectMapper.readValue(str, FeatureData.class);
        List<Properties> places = featureData.getFeatures().stream().map(Feature::getProperties).toList();
        return places;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(List<Feature> features) {
        this.features = features;
    }
}
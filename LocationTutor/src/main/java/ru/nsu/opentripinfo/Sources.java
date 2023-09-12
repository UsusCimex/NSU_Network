package ru.nsu.opentripinfo;

import java.util.List;

public class Sources {
    private String geometry;
    private List<String> attributes;

    public String getGeometry() {
        return geometry;
    }

    public void setGeometry(String geometry) {
        this.geometry = geometry;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<String> attributes) {
        this.attributes = attributes;
    }
}

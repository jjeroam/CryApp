package com.example.babycry.helper;

import org.tensorflow.lite.support.label.Category;

import java.util.List;

public class RecordingHistory {
    private long timestamp;
    private List<Category> topCategories;

    public RecordingHistory(long timestamp, List<Category> topCategories) {
        this.timestamp = timestamp;
        this.topCategories = topCategories;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<Category> getTopCategories() {
        return topCategories;
    }
}

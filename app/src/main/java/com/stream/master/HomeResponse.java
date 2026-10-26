package com.stream.master;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeResponse {

    @SerializedName("status")
    private String status;

    @SerializedName("data")
    private Map<String, List<Movie>> data;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, List<Movie>> getData() {
        return data;
    }

    public void setData(Map<String, List<Movie>> data) {
        this.data = data;
    }

    public List<Movie> getCategory(String categoryName) {
        if (data != null && data.containsKey(categoryName) && data.get(categoryName) != null) {
            return data.get(categoryName);
        }
        return new ArrayList<>();
    }
}
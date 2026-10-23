package com.stream.master;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class MovieResponse {
    @SerializedName(value = "results", alternate = {"videos", "movies", "data"})
    private List<Movie> results;

    @SerializedName("page")
    private int page;

    public List<Movie> getResults() {
        return results != null ? results : new ArrayList<>();
    }

    public int getPage() {
        return page;
    }
}

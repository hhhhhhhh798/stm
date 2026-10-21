package com.stream.master;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** GET api/series?lang=&page=&limit=  ->  { "page": 1, "results": [ Series, ... ] } */
public class SeriesResponse {

    @SerializedName(value = "results", alternate = {"series", "shows", "videos", "data"})
    private List<Series> results;

    @SerializedName("page")
    private int page;

    public List<Series> getResults() {
        List<Series> clean = new ArrayList<>();
        if (results != null) {
            for (Series s : results) {
                if (s != null && !s.getVid().isEmpty()) clean.add(s);
            }
        }
        return clean;
    }

    public int getPage() { return page; }
}

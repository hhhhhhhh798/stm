package com.stream.master;

import java.util.List;

public class CategoryShelf {
    private String title;
    private List<Movie> movies;
    private int currentPage = 1;

    public CategoryShelf(String title, List<Movie> movies) {
        this.title = title;
        this.movies = movies;
    }

    public String getTitle() { return title; }
    public List<Movie> getMovies() { return movies; }
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
}

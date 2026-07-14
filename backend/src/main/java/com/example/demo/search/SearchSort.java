package com.example.demo.search;

public enum SearchSort {
    RELEVANCE,
    HOT,
    TIME,
    SIGNUP,
    COMPOSITE;

    public static SearchSort from(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        return SearchSort.valueOf(value.trim().toUpperCase());
    }
}

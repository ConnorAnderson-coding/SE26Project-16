package com.example.demo.search;

public enum SearchMode {
    KEYWORD,
    SEMANTIC,
    HYBRID;

    public static SearchMode from(String value) {
        if (value == null || value.isBlank()) {
            return HYBRID;
        }
        return SearchMode.valueOf(value.trim().toUpperCase());
    }
}

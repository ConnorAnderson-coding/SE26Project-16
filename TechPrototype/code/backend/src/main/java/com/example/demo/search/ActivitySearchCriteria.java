package com.example.demo.search;

public record ActivitySearchCriteria(
        String keyword,
        String category,
        String status,
        String location,
        SearchMode mode,
        SearchSort sort,
        double matchWeight,
        int page,
        int size) {

    public ActivitySearchCriteria {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
        if (mode == null) {
            mode = SearchMode.HYBRID;
        }
        if (sort == null) {
            sort = SearchSort.RELEVANCE;
        }
        if (matchWeight < 0.0) {
            matchWeight = 0.0;
        }
        if (matchWeight > 1.0) {
            matchWeight = 1.0;
        }
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}

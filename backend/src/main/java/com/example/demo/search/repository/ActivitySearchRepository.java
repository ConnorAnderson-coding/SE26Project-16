package com.example.demo.search.repository;

import com.example.demo.search.ActivitySearchCriteria;
import com.example.demo.search.ActivitySearchHit;

import java.util.List;

public interface ActivitySearchRepository {

    List<ActivitySearchHit> searchKeyword(ActivitySearchCriteria criteria, int recallSize);

    List<ActivitySearchHit> searchSemantic(ActivitySearchCriteria criteria, int recallSize);

    long count(ActivitySearchCriteria criteria);

    /** Total documents in the activities index (0 means not synced yet). */
    long countAll();
}

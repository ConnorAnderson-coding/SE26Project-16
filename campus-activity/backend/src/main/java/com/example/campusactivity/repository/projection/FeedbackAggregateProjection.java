package com.example.campusactivity.repository.projection;

public interface FeedbackAggregateProjection {
    String getUserId();

    Long getFeedbackCount();

    Long getValidRatingCount();

    Long getValidRatingSum();

    Long getInvalidRatingCount();
}

package com.example.campusactivity.repository.projection;

public interface ApprovedSignupCategoryProjection {
    String getUserId();

    String getMatchedActivityId();

    String getCategory();

    Long getParticipationCount();
}

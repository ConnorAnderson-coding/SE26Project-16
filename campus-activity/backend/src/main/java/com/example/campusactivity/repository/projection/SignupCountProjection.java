package com.example.campusactivity.repository.projection;

public interface SignupCountProjection {
    String getUserId();

    Long getSignupCount();

    Long getApprovedSignupCount();

    Long getUnknownSignupStatusCount();
}

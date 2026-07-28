package com.example.demo.repository.projection;

public interface UserFeedbackAggregate extends UserBehaviorCount {

    Double getAverageRating();
}

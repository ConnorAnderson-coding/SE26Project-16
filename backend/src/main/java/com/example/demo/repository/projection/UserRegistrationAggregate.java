package com.example.demo.repository.projection;

public interface UserRegistrationAggregate extends UserBehaviorCount {

    Long getApprovedCount();
}

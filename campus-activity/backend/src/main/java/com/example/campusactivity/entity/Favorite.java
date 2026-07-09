package com.example.campusactivity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;

import java.util.UUID;

@Entity
public class Favorite {
    @Id
    private String id;

    private String userId;
    private String activityId;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = "fav-" + UUID.randomUUID();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }
}

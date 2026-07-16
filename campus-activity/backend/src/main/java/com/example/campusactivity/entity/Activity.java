package com.example.campusactivity.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Activity {
    @Id
    private String id;
    private LocalDateTime createdAt; // New field for creation timestamp
    @LastModifiedDate
    private LocalDateTime lastModifiedAt;

    @NotBlank
    private String title;

    private String category;

    @Column(length = 3000)
    private String description;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private String organizerId;
    private String organizerName;
    private String college;

    @Column(length = 1000)
    private String poster;

    private Integer maxParticipants = 50;
    private Integer viewCount = 0; // New field for view count
    private Integer signupCount = 0;
    private Integer favoriteCount = 0;
    private Double hotness = 0.0; // New field for hotness calculation
    private String status = "published";

    @ElementCollection
    private List<String> tags = new ArrayList<>();

    private String checkInCode;

    @Column(length = 3000)
    private String recordSummary;

    @ElementCollection
    private List<String> recordPhotos = new ArrayList<>();

    private LocalDateTime recordPublishedAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (checkInCode == null || checkInCode.isBlank()) {
            checkInCode = "CK" + id.replace("-", "").substring(0, 6).toUpperCase();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastModifiedAt() {
        return lastModifiedAt;
    }

    public void setLastModifiedAt(LocalDateTime lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getPoster() {
        return poster;
    }

    public void setPoster(String poster) {
        this.poster = poster;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getSignupCount() {
        return signupCount;
    }

    public void setSignupCount(Integer signupCount) {
        this.signupCount = signupCount;
    }

    public Integer getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(Integer favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public Double getHotness() {
        return hotness;
    }

    public void setHotness(Double hotness) {
        this.hotness = hotness;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public String getCheckInCode() {
        return checkInCode;
    }

    public void setCheckInCode(String checkInCode) {
        this.checkInCode = checkInCode;
    }

    public String getRecordSummary() {
        return recordSummary;
    }

    public void setRecordSummary(String recordSummary) {
        this.recordSummary = recordSummary;
    }

    public List<String> getRecordPhotos() {
        return recordPhotos;
    }

    public void setRecordPhotos(List<String> recordPhotos) {
        this.recordPhotos = recordPhotos == null ? new ArrayList<>() : recordPhotos;
    }

    public LocalDateTime getRecordPublishedAt() {
        return recordPublishedAt;
    }

    public void setRecordPublishedAt(LocalDateTime recordPublishedAt) {
        this.recordPublishedAt = recordPublishedAt;
    }
}

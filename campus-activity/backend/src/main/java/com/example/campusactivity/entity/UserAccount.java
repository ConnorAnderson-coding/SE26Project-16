package com.example.campusactivity.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id
    @NotBlank
    private String id;

    @NotBlank
    @JsonIgnore
    private String password;

    @NotBlank
    private String name;

    private String role = "student";
    private String college;
    private String grade;

    @ElementCollection
    private List<String> interests = new ArrayList<>();

    @ElementCollection
    private List<String> availableTime = new ArrayList<>();

    @ElementCollection
    private List<String> friends = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests == null
                ? new ArrayList<>()
                : new ArrayList<>(interests);
    }

    public List<String> getAvailableTime() {
        return availableTime;
    }

    public void setAvailableTime(List<String> availableTime) {
        this.availableTime = availableTime == null
                ? new ArrayList<>()
                : new ArrayList<>(availableTime);
    }

    public List<String> getFriends() {
        return friends;
    }

    public void setFriends(List<String> friends) {
        this.friends = friends == null
                ? new ArrayList<>()
                : new ArrayList<>(friends);
    }
}

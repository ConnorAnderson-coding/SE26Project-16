package com.example.demo.service;

import com.example.demo.entity.Activity;
import com.example.demo.entity.User;
import com.example.demo.security.UserPrincipal;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;

abstract class ServiceTestSupport {

    static User login(String id, String role) {
        User user = user(id, role);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new UserPrincipal(user), null));
        return user;
    }

    static User user(String id, String role) {
        User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setRole(role);
        user.setCollege("Software");
        user.setGrade("2024");
        user.setInterests(new ArrayList<>());
        user.setAvailableTime(new ArrayList<>());
        user.setCreatedAt(LocalDateTime.now().minusDays(1));
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    static Activity activity(long id, User organizer) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setTitle("Activity " + id);
        activity.setCategory("academic");
        activity.setDescription("Description");
        activity.setStartTime(LocalDateTime.now().minusMinutes(10));
        activity.setEndTime(LocalDateTime.now().plusMinutes(10));
        activity.setLocation("Room 1");
        activity.setOrganizer(organizer);
        activity.setOrganizerId(organizer.getId());
        activity.setCollege(organizer.getCollege());
        activity.setMaxParticipants(100);
        activity.setSignupCount(10);
        activity.setFavoriteCount(2);
        activity.setViewCount(20);
        activity.setCheckInCount(3);
        activity.setHotnessScore(1.0);
        activity.setStatus("published");
        activity.setTags(new ArrayList<>());
        activity.setCreatedAt(LocalDateTime.now().minusHours(1));
        activity.setUpdatedAt(LocalDateTime.now());
        return activity;
    }
}

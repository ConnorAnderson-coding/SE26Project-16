package com.example.demo.dto;

import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityRecord;
import com.example.demo.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DtoMapperTest {

    @Test
    void toUserResponseMapsFields() {
        User user = new User();
        user.setId("u1");
        user.setName("张三");
        user.setRole("student");
        user.setCollege("计算机学院");
        user.setGrade("2024");
        user.setInterests(List.of("编程"));

        UserResponse response = DtoMapper.toUserResponse(user);

        assertEquals("u1", response.getId());
        assertEquals("张三", response.getName());
        assertEquals("student", response.getRole());
        assertEquals(List.of("编程"), response.getInterests());
    }

    @Test
    void toActivityResponseIncludesOrganizerAndRecord() {
        User organizer = new User();
        organizer.setId("org1");
        organizer.setName("李老师");

        ActivityRecord record = new ActivityRecord();
        record.setSummary("活动总结");
        record.setPhotos(List.of("photo.jpg"));
        record.setPublishedAt(LocalDateTime.of(2026, 7, 1, 12, 0));

        Activity activity = new Activity();
        activity.setId(1L);
        activity.setTitle("讲座");
        activity.setCategory("讲座");
        activity.setDescription("描述");
        activity.setStartTime(LocalDateTime.of(2026, 7, 2, 10, 0));
        activity.setEndTime(LocalDateTime.of(2026, 7, 2, 12, 0));
        activity.setLocation("A101");
        activity.setOrganizer(organizer);
        activity.setCollege("计算机学院");
        activity.setMaxParticipants(100);
        activity.setSignupCount(10);
        activity.setFavoriteCount(5);
        activity.setStatus("published");
        activity.setTags(List.of("AI"));
        activity.setRecord(record);

        ActivityResponse response = DtoMapper.toActivityResponse(activity);

        assertEquals("org1", response.getOrganizerId());
        assertEquals("李老师", response.getOrganizerName());
        assertNotNull(response.getRecord());
        assertEquals("活动总结", response.getRecord().getSummary());
    }
}

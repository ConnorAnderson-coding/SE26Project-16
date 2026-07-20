package com.example.demo.dto;

import com.example.demo.dto.response.ActivityRecordResponse;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.dto.response.CheckInResponse;
import com.example.demo.dto.response.FeedbackResponse;
import com.example.demo.dto.response.RegistrationResponse;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityRecord;
import com.example.demo.entity.CheckIn;
import com.example.demo.entity.Feedback;
import com.example.demo.entity.Registration;
import com.example.demo.entity.User;

import java.util.Collections;
import java.util.List;

public final class DtoMapper {

    private DtoMapper() {
    }

    public static UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .role(user.getRole())
                .jaccount(user.getJaccount())
                .jaccountType(user.getJaccountType())
                .college(user.getCollege())
                .grade(user.getGrade())
                .interests(nullToEmpty(user.getInterests()))
                .availableTime(nullToEmpty(user.getAvailableTime()))
                .build();
    }

    public static ActivityRecordResponse toRecordResponse(ActivityRecord record) {
        if (record == null) {
            return null;
        }
        return ActivityRecordResponse.builder()
                .summary(record.getSummary())
                .photos(nullToEmpty(record.getPhotos()))
                .publishedAt(record.getPublishedAt())
                .build();
    }

    public static ActivityResponse toActivityResponse(Activity activity) {
        return ActivityResponse.builder()
                .id(activity.getId())
                .title(activity.getTitle())
                .category(activity.getCategory())
                .description(activity.getDescription())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .location(activity.getLocation())
                .organizerId(activity.getOrganizer() != null ? activity.getOrganizer().getId() : activity.getOrganizerId())
                .organizerName(activity.getOrganizer() != null ? activity.getOrganizer().getName() : null)
                .college(activity.getCollege())
                .poster(activity.getPoster())
                .maxParticipants(activity.getMaxParticipants())
                .signupCount(activity.getSignupCount())
                .favoriteCount(activity.getFavoriteCount())
                .hotnessScore(activity.getHotnessScore())
                .status(activity.getStatus())
                .tags(nullToEmpty(activity.getTags()))
                .checkInCode(activity.getCheckInCode())
                .latitude(activity.getLatitude())
                .longitude(activity.getLongitude())
                .checkInRadiusMeters(activity.getCheckInRadiusMeters())
                .record(toRecordResponse(activity.getRecord()))
                .build();
    }

    public static CheckInResponse toCheckInResponse(CheckIn checkIn) {
        Activity activity = checkIn.getActivity();
        User user = checkIn.getUser();
        return CheckInResponse.builder()
                .id(checkIn.getId())
                .activityId(activity != null ? activity.getId() : checkIn.getActivityId())
                .activityTitle(activity != null ? activity.getTitle() : null)
                .userId(user != null ? user.getId() : checkIn.getUserId())
                .userName(user != null ? user.getName() : null)
                .method(checkIn.getMethod())
                .time(checkIn.getCheckedAt())
                .latitude(checkIn.getLatitude())
                .longitude(checkIn.getLongitude())
                .distanceMeters(checkIn.getDistanceMeters())
                .build();
    }

    public static RegistrationResponse toRegistrationResponse(Registration registration) {
        Activity activity = registration.getActivity();
        User user = registration.getUser();
        return RegistrationResponse.builder()
                .id(registration.getId())
                .activityId(activity != null ? activity.getId() : registration.getActivityId())
                .activityTitle(activity != null ? activity.getTitle() : null)
                .userId(user != null ? user.getId() : registration.getUserId())
                .userName(user != null ? user.getName() : null)
                .college(user != null ? user.getCollege() : null)
                .status(registration.getStatus())
                .createdAt(registration.getCreatedAt())
                .location(activity != null ? activity.getLocation() : null)
                .startTime(activity != null ? activity.getStartTime() : null)
                .build();
    }

    public static FeedbackResponse toFeedbackResponse(Feedback feedback) {
        return FeedbackResponse.builder()
                .id(feedback.getId())
                .activityId(feedback.getActivity() != null ? feedback.getActivity().getId() : feedback.getActivityId())
                .activityTitle(feedback.getActivity() != null ? feedback.getActivity().getTitle() : null)
                .userId(feedback.getUser() != null ? feedback.getUser().getId() : feedback.getUserId())
                .userName(feedback.getUser() != null ? feedback.getUser().getName() : null)
                .rating(feedback.getRating())
                .content(feedback.getContent())
                .createdAt(feedback.getCreatedAt())
                .build();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
}

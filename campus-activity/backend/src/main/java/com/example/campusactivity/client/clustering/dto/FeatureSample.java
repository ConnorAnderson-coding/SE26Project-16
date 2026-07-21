package com.example.campusactivity.client.clustering.dto;

import java.util.List;
import java.util.Map;

public record FeatureSample(
        String userId,
        List<String> interests,
        String college,
        String grade,
        List<String> availableTime,
        Integer signupCount,
        Integer approvedSignupCount,
        Integer favoriteCount,
        Integer checkInCount,
        Integer feedbackCount,
        Double averageRating,
        Map<String, Integer> categoryParticipationCounts
) {
    public FeatureSample {
        userId = ClusteringContractChecks.identifier(userId, "userId");
        interests = ClusteringContractChecks.stringList(interests, "interests", true);
        if (college != null) {
            college = ClusteringContractChecks.string(college, "college");
        }
        if (grade != null) {
            grade = ClusteringContractChecks.string(grade, "grade");
        }
        availableTime = ClusteringContractChecks.stringList(availableTime, "availableTime", true);
        int checkedSignupCount = ClusteringContractChecks.nonNegative(signupCount, "signupCount");
        int checkedApprovedCount = ClusteringContractChecks.nonNegative(
                approvedSignupCount,
                "approvedSignupCount"
        );
        if (checkedApprovedCount > checkedSignupCount) {
            throw new IllegalArgumentException("approvedSignupCount 不能大于 signupCount");
        }
        ClusteringContractChecks.nonNegative(favoriteCount, "favoriteCount");
        ClusteringContractChecks.nonNegative(checkInCount, "checkInCount");
        ClusteringContractChecks.nonNegative(feedbackCount, "feedbackCount");
        if (averageRating != null) {
            double checkedRating = ClusteringContractChecks.finite(averageRating, "averageRating");
            if (checkedRating < 1.0 || checkedRating > 5.0) {
                throw new IllegalArgumentException("averageRating 必须在 1.0 到 5.0 之间");
            }
        }
        categoryParticipationCounts = ClusteringContractChecks.countMap(
                categoryParticipationCounts,
                "categoryParticipationCounts"
        );
    }
}

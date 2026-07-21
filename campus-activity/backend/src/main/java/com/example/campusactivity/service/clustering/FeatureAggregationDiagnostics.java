package com.example.campusactivity.service.clustering;

public record FeatureAggregationDiagnostics(
        long excludedAdminCount,
        long invalidUserCount,
        long countOverflowUserCount,
        long ignoredOrphanBehaviorCount,
        long missingActivityCount,
        long blankActivityCategoryCount,
        long invalidRatingCount,
        long unknownSignupStatusCount
) {
}

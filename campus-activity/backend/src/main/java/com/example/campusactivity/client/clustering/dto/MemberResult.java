package com.example.campusactivity.client.clustering.dto;

public record MemberResult(
        String userId,
        Integer clusterNo,
        Double coordinateX,
        Double coordinateY,
        Double distanceToCenter
) {
    public MemberResult {
        userId = ClusteringContractChecks.identifier(userId, "members[].userId");
        ClusteringContractChecks.nonNegative(clusterNo, "members[].clusterNo");
        validateCoordinate(coordinateX, "members[].coordinateX");
        validateCoordinate(coordinateY, "members[].coordinateY");
        double checkedDistance = ClusteringContractChecks.finite(
                distanceToCenter,
                "members[].distanceToCenter"
        );
        if (checkedDistance < 0.0) {
            throw new IllegalArgumentException("members[].distanceToCenter 不能为负数");
        }
    }

    private static void validateCoordinate(Double value, String field) {
        double checked = ClusteringContractChecks.finite(value, field);
        if (checked < 0.0 || checked > 100.0) {
            throw new IllegalArgumentException(field + " 必须在 0 到 100 之间");
        }
    }
}

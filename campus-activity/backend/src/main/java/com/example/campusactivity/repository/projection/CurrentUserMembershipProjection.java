package com.example.campusactivity.repository.projection;

public interface CurrentUserMembershipProjection {
    String getPointId();

    String getCommunityId();

    Integer getClusterNo();

    String getCommunityName();

    String getColor();

    Double getCoordinateX();

    Double getCoordinateY();

    Double getDistanceToCenter();
}

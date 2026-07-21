package com.example.campusactivity.repository.projection;

public interface CommunityMemberPointProjection {
    String getPointId();

    String getCommunityId();

    Integer getClusterNo();

    Double getCoordinateX();

    Double getCoordinateY();

    Boolean getCurrentUser();
}

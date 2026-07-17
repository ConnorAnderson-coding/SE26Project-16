package com.example.campusactivity.repository.projection;

public interface CommunityQueryProjection {
    String getCommunityId();

    Integer getClusterNo();

    String getName();

    String getDescription();

    Integer getMemberCount();

    String getTopInterestsJson();

    String getColor();
}

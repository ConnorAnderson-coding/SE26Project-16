package com.example.campusactivity.service.clustering;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ClusteringIdentifierGenerator {
    public String newRunId() {
        return newUuid();
    }

    public String newVersion() {
        return newUuid();
    }

    public String newCommunityId() {
        return newUuid();
    }

    public String newMemberId() {
        return newUuid();
    }

    private static String newUuid() {
        return UUID.randomUUID().toString();
    }
}

package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.TriggerClusteringRequest;
import com.example.campusactivity.dto.clustering.TriggerClusteringResponse;
import com.example.campusactivity.service.clustering.ClusteringSubmissionResult;
import com.example.campusactivity.service.clustering.CommunityClusteringSubmissionFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/admin/community-clustering/runs")
public class AdminCommunityClusteringSubmissionController {
    private final TriggerClusteringRequestParser requestParser;
    private final CommunityClusteringSubmissionFacade submissionFacade;

    public AdminCommunityClusteringSubmissionController(
            TriggerClusteringRequestParser requestParser,
            CommunityClusteringSubmissionFacade submissionFacade
    ) {
        this.requestParser = requestParser;
        this.submissionFacade = submissionFacade;
    }

    @PostMapping
    public ResponseEntity<TriggerClusteringResponse> trigger(
            @RequestBody(required = false) byte[] body,
            Authentication authentication
    ) {
        TriggerClusteringRequest request = requestParser.parse(body);
        ClusteringSubmissionResult submitted = submissionFacade.submit(
                request.resolvedClusterCount(),
                authentication.getName()
        );
        URI location = URI.create(
                "/api/v1/admin/community-clustering/runs/" + submitted.runId()
        );
        return ResponseEntity.accepted()
                .location(location)
                .body(TriggerClusteringResponse.from(submitted));
    }
}

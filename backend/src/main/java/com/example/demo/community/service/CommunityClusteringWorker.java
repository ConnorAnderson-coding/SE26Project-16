package com.example.demo.community.service;

import com.example.demo.community.client.ClusteringClient;
import com.example.demo.community.client.ClusteringClientException;
import com.example.demo.community.client.ClusteringContracts.Request;
import com.example.demo.community.client.ClusteringContracts.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "community-clustering.python", name = "enabled", havingValue = "true")
public class CommunityClusteringWorker {

    private final ClusteringRequestFactory requestFactory;
    private final ClusteringClient client;
    private final ClusteringRunLifecycleService lifecycleService;

    public CommunityClusteringWorker(
            ClusteringRequestFactory requestFactory,
            ClusteringClient client,
            ClusteringRunLifecycleService lifecycleService
    ) {
        this.requestFactory = requestFactory;
        this.client = client;
        this.lifecycleService = lifecycleService;
    }

    public void execute(String runId) {
        try {
            Request request = requestFactory.buildRequest(runId);
            Response response = client.runClustering(request);
            lifecycleService.complete(runId, response);
        } catch (ClusteringClientException exception) {
            String code = exception.getRemoteCode() == null
                    ? "PYTHON_" + exception.getKind().name()
                    : exception.getRemoteCode();
            lifecycleService.markFailed(runId, code, "Python 聚类服务调用失败");
        } catch (RuntimeException exception) {
            lifecycleService.markFailed(runId, "INTERNAL_ERROR", "聚类运行执行失败");
        }
    }
}

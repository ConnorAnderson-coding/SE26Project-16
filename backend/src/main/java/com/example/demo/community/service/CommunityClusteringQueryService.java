package com.example.demo.community.service;

import com.example.demo.community.api.ClusteringApiException;
import com.example.demo.community.api.CommunityClusteringDtos.AdminMember;
import com.example.demo.community.api.CommunityClusteringDtos.CommunitySummary;
import com.example.demo.community.api.CommunityClusteringDtos.CommunityView;
import com.example.demo.community.api.CommunityClusteringDtos.Failure;
import com.example.demo.community.api.CommunityClusteringDtos.LatestResult;
import com.example.demo.community.api.CommunityClusteringDtos.LatestRun;
import com.example.demo.community.api.CommunityClusteringDtos.MemberPage;
import com.example.demo.community.api.CommunityClusteringDtos.Metrics;
import com.example.demo.community.api.CommunityClusteringDtos.MyCommunity;
import com.example.demo.community.api.CommunityClusteringDtos.MyMembership;
import com.example.demo.community.api.CommunityClusteringDtos.PageResponse;
import com.example.demo.community.api.CommunityClusteringDtos.Point;
import com.example.demo.community.api.CommunityClusteringDtos.RunDetail;
import com.example.demo.community.api.CommunityClusteringDtos.RunSummary;
import com.example.demo.entity.ClusteringRun;
import com.example.demo.entity.ClusteringRunStatus;
import com.example.demo.entity.Community;
import com.example.demo.entity.CommunityMember;
import com.example.demo.repository.ClusteringRunRepository;
import com.example.demo.repository.CommunityMemberRepository;
import com.example.demo.repository.CommunityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class CommunityClusteringQueryService {

    private final ClusteringRunRepository runRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;

    public CommunityClusteringQueryService(
            ClusteringRunRepository runRepository,
            CommunityRepository communityRepository,
            CommunityMemberRepository memberRepository
    ) {
        this.runRepository = runRepository;
        this.communityRepository = communityRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<RunSummary> runs(int page, int size) {
        validatePage(page, size);
        Page<ClusteringRun> result = runRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size));
        return new PageResponse<>(result.getContent().stream().map(this::summary).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RunDetail run(String runId) {
        ClusteringRun run = runRepository.findById(validId(runId))
                .orElseThrow(() -> new ClusteringApiException(HttpStatus.NOT_FOUND, "未找到指定的聚类任务"));
        Metrics metrics = metrics(run);
        Failure failure = run.getStatus() == ClusteringRunStatus.FAILED
                ? new Failure(safeFailureCode(run.getErrorCode()), safeFailureMessage(run.getErrorCode())) : null;
        return new RunDetail(run.getId(), run.getVersion(), run.getAlgorithm().name(), run.getClusterCount(),
                run.getRandomState(), run.getStatus().name(), run.getSampleCount(), run.getFeatureDimension(),
                run.getFeatureSchemaVersion(), metrics, failure, run.getCreatedAt(), run.getStartedAt(),
                run.getFinishedAt(), run.getCreatedBy());
    }

    @Transactional(readOnly = true)
    public LatestResult latest(String currentUserId) {
        ClusteringRun run = latestRun();
        List<Community> communities = communityRepository.findByRunOrderByClusterNoAsc(run);
        List<CommunityMember> members = memberRepository.findByRun(run);
        validateSuccessfulGraph(run, communities, members);
        List<CommunityView> views = communities.stream().map(community -> {
            List<Point> points = members.stream()
                    .filter(member -> member.getCommunity().getId().equals(community.getId()))
                    .sorted(Comparator.comparing(CommunityMember::getId))
                    .map(member -> new Point(member.getId(), member.getCoordinateX(), member.getCoordinateY(),
                            member.getUser().getId().equals(currentUserId)))
                    .toList();
            return new CommunityView(community.getId(), community.getClusterNo(), community.getName(),
                    community.getDescription(), community.getMemberCount(), List.copyOf(community.getTopInterests()),
                    community.getColor(), points);
        }).toList();
        return new LatestResult(new LatestRun(run.getId(), run.getVersion(), run.getAlgorithm().name(),
                run.getClusterCount(), run.getSampleCount(), run.getFinishedAt()), views);
    }

    @Transactional(readOnly = true)
    public MyCommunity mine(String userId) {
        ClusteringRun run = latestRun();
        MyMembership membership = memberRepository.findByRun(run).stream()
                .filter(member -> member.getUser().getId().equals(userId))
                .findFirst()
                .map(member -> new MyMembership(member.getCommunity().getId(), member.getCommunity().getClusterNo(),
                        member.getCommunity().getName(), member.getCommunity().getColor(), member.getId(),
                        member.getCoordinateX(), member.getCoordinateY(), member.getDistanceToCenter()))
                .orElse(null);
        return new MyCommunity(run.getId(), run.getVersion(), membership);
    }

    @Transactional(readOnly = true)
    public MemberPage members(String communityId, int page, int size) {
        validatePage(page, size);
        Community community = communityRepository.findById(validId(communityId))
                .orElseThrow(() -> new ClusteringApiException(HttpStatus.NOT_FOUND, "未找到指定社区"));
        Page<CommunityMember> result = memberRepository
                .findByCommunityOrderByDistanceToCenterAscUserIdAsc(community, PageRequest.of(page, size));
        List<AdminMember> items = result.getContent().stream().map(member -> new AdminMember(
                member.getUser().getId(), member.getUser().getName(), member.getUser().getCollege(),
                member.getUser().getGrade(), member.getId(), member.getCoordinateX(), member.getCoordinateY(),
                member.getDistanceToCenter())).toList();
        CommunitySummary summary = new CommunitySummary(community.getId(), community.getRun().getId(),
                community.getClusterNo(), community.getName(), community.getColor(), community.getMemberCount());
        return new MemberPage(summary, items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private ClusteringRun latestRun() {
        return runRepository.findLatestSuccessful()
                .orElseThrow(() -> new ClusteringApiException(HttpStatus.NOT_FOUND, "当前还没有可用的社区聚类结果"));
    }

    private RunSummary summary(ClusteringRun run) {
        return new RunSummary(run.getId(), run.getVersion(), run.getAlgorithm().name(), run.getClusterCount(),
                run.getRandomState(), run.getStatus().name(), run.getSampleCount(), run.getFeatureSchemaVersion(),
                run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt(), run.getCreatedBy());
    }

    @SuppressWarnings("unchecked")
    private Metrics metrics(ClusteringRun run) {
        if (run.getStatus() != ClusteringRunStatus.SUCCESS || run.getMetrics() == null) {
            return null;
        }
        Object inertia = run.getMetrics().get("inertia");
        Object ratios = run.getMetrics().get("pcaExplainedVarianceRatio");
        if (!(inertia instanceof Number number) || !(ratios instanceof List<?> values)
                || values.size() != 2 || values.stream().anyMatch(value -> !(value instanceof Number))) {
            throw new ClusteringApiException(HttpStatus.INTERNAL_SERVER_ERROR, "聚类结果数据无效");
        }
        List<Double> converted = new ArrayList<>(2);
        values.forEach(value -> converted.add(((Number) value).doubleValue()));
        if (!Double.isFinite(number.doubleValue()) || number.doubleValue() < 0.0
                || converted.stream().anyMatch(value -> !Double.isFinite(value) || value < 0.0 || value > 1.0)) {
            throw new ClusteringApiException(HttpStatus.INTERNAL_SERVER_ERROR, "聚类结果数据无效");
        }
        return new Metrics(number.doubleValue(), List.copyOf(converted));
    }

    private static void validateSuccessfulGraph(
            ClusteringRun run,
            List<Community> communities,
            List<CommunityMember> members
    ) {
        if (run.getClusterCount() == null || run.getSampleCount() == null
                || communities.size() != run.getClusterCount() || members.size() != run.getSampleCount()
                || communities.stream().anyMatch(community -> community.getMemberCount() == null
                || community.getMemberCount() != members.stream()
                .filter(member -> member.getCommunity().getId().equals(community.getId())).count())
                || members.stream().anyMatch(member -> !finiteCoordinate(member.getCoordinateX())
                || !finiteCoordinate(member.getCoordinateY())
                || member.getDistanceToCenter() == null || !Double.isFinite(member.getDistanceToCenter())
                || member.getDistanceToCenter() < 0.0)) {
            throw new ClusteringApiException(HttpStatus.INTERNAL_SERVER_ERROR, "聚类结果数据无效");
        }
    }

    private static boolean finiteCoordinate(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 && value <= 100.0;
    }

    private static String validId(String id) {
        if (id == null || id.isBlank() || id.length() > 64) {
            throw new ClusteringApiException(HttpStatus.BAD_REQUEST, "聚类标识无效");
        }
        return id;
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ClusteringApiException(HttpStatus.BAD_REQUEST, "分页参数无效");
        }
    }

    private static String safeFailureCode(String code) {
        return code == null || code.isBlank() ? "CLUSTERING_FAILED" : code;
    }

    private static String safeFailureMessage(String code) {
        return safeFailureCode(code) + ": 聚类任务执行失败";
    }
}

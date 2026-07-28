"""K-Means clustering, distance calculation, PCA and result assembly."""

from __future__ import annotations

from collections import Counter
from typing import Sequence

import numpy as np
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA

from .exceptions import (
    ClusteringServiceError,
    clustering_computation_failed,
    invalid_cluster_count,
    invalid_feature_schema,
    invalid_sample_data,
)
from .preprocessing import ensure_finite_matrix, preprocess_samples
from .schemas import (
    ClusteringMetrics,
    ClusteringRequest,
    ClusteringResponse,
    CommunitySummary,
    FeatureSample,
    MemberResult,
)


SUPPORTED_FEATURE_SCHEMA = "community-features-v2"
SUPPORTED_RANDOM_STATE = 42
PCA_AXIS_ABS_TOLERANCE = 1e-12
PCA_AXIS_REL_TOLERANCE = 1e-9


def validate_request(request: ClusteringRequest) -> None:
    sample_count = len(request.samples)
    if sample_count < 2:
        raise invalid_sample_data(
            reason="INSUFFICIENT_SAMPLES",
            details={"min": 2, "actual": sample_count},
            message="用户样本至少需要 2 条",
        )

    seen_user_ids: set[str] = set()
    duplicate_user_ids: list[str] = []
    for sample in request.samples:
        if sample.userId in seen_user_ids and sample.userId not in duplicate_user_ids:
            duplicate_user_ids.append(sample.userId)
        seen_user_ids.add(sample.userId)
    if duplicate_user_ids:
        raise invalid_sample_data(
            reason="DUPLICATE_USER_ID",
            details={"userIds": duplicate_user_ids},
            message="用户 ID 不得重复",
        )

    if request.clusterCount < 2 or request.clusterCount > sample_count:
        raise invalid_cluster_count(
            actual=request.clusterCount,
            sample_count=sample_count,
        )
    if request.featureSchemaVersion != SUPPORTED_FEATURE_SCHEMA:
        raise invalid_feature_schema(actual=request.featureSchemaVersion)
    if request.algorithm != "KMEANS":
        raise invalid_sample_data(
            reason="UNSUPPORTED_ALGORITHM",
            details={"supported": "KMEANS"},
            message="聚类算法必须为 KMEANS",
        )
    if request.randomState != SUPPORTED_RANDOM_STATE:
        raise invalid_sample_data(
            reason="INVALID_RANDOM_STATE",
            details={"supported": SUPPORTED_RANDOM_STATE},
            message="randomState 必须为 42",
        )


def _normalize_axis(values: np.ndarray) -> np.ndarray:
    minimum = float(np.min(values))
    maximum = float(np.max(values))
    value_range = maximum - minimum
    comparison_scale = max(abs(minimum), abs(maximum))
    tolerance = (
        PCA_AXIS_ABS_TOLERANCE + PCA_AXIS_REL_TOLERANCE * comparison_scale
    )
    if value_range <= tolerance:
        return np.full(values.shape, 50.0, dtype=float)
    normalized = (values - minimum) * (100.0 / value_range)
    return np.clip(normalized, 0.0, 100.0)


def project_to_2d(matrix: object) -> tuple[np.ndarray, list[float]]:
    numeric_matrix = ensure_finite_matrix(matrix)
    sample_count, feature_count = numeric_matrix.shape
    if sample_count == 0:
        raise invalid_sample_data(
            reason="EMPTY_SAMPLES",
            message="PCA 输入不能为空",
        )

    centered_matrix = numeric_matrix - np.mean(numeric_matrix, axis=0)
    if not np.isfinite(centered_matrix).all():
        raise clustering_computation_failed(reason="NON_FINITE_PCA_RESULT")
    try:
        effective_rank = int(np.linalg.matrix_rank(centered_matrix))
    except np.linalg.LinAlgError as error:
        raise clustering_computation_failed(reason="NON_FINITE_PCA_RESULT") from error

    if effective_rank == 0:
        projected = np.zeros((sample_count, 2), dtype=float)
        ratios = np.zeros(2, dtype=float)
    else:
        component_count = min(2, effective_rank, sample_count, feature_count)
        try:
            pca = PCA(n_components=component_count, svd_solver="full")
            partial_projection = np.asarray(pca.fit_transform(numeric_matrix), dtype=float)
            partial_ratios = np.asarray(pca.explained_variance_ratio_, dtype=float)
        except (TypeError, ValueError, ArithmeticError) as error:
            raise clustering_computation_failed(reason="NON_FINITE_PCA_RESULT") from error

        if (
            partial_projection.ndim != 2
            or partial_projection.shape != (sample_count, component_count)
            or partial_ratios.shape != (component_count,)
            or not np.isfinite(partial_projection).all()
            or not np.isfinite(partial_ratios).all()
        ):
            raise clustering_computation_failed(reason="NON_FINITE_PCA_RESULT")

        # PCA signs are mathematically arbitrary. Orient each component by its
        # largest absolute loading so repeated executions use the same direction.
        components = np.asarray(pca.components_, dtype=float)
        if (
            components.shape != (component_count, feature_count)
            or not np.isfinite(components).all()
        ):
            raise clustering_computation_failed(reason="NON_FINITE_PCA_RESULT")
        for index in range(component_count):
            component = components[index]
            pivot = int(np.argmax(np.abs(component)))
            if component[pivot] < 0.0:
                partial_projection[:, index] *= -1.0

        projected = np.zeros((sample_count, 2), dtype=float)
        projected[:, :component_count] = partial_projection
        ratios = np.zeros(2, dtype=float)
        ratios[:component_count] = np.clip(partial_ratios, 0.0, 1.0)
        ratios[np.isclose(ratios, 0.0, atol=np.finfo(float).eps)] = 0.0

    coordinates = np.column_stack(
        (_normalize_axis(projected[:, 0]), _normalize_axis(projected[:, 1]))
    )
    if (
        not np.isfinite(coordinates).all()
        or not np.isfinite(ratios).all()
        or np.any(coordinates < 0.0)
        or np.any(coordinates > 100.0)
    ):
        raise clustering_computation_failed(reason="NON_FINITE_PCA_RESULT")
    return coordinates, [float(ratios[0]), float(ratios[1])]


def _canonicalize_clusters(
    labels: np.ndarray, centers: np.ndarray
) -> tuple[np.ndarray, np.ndarray]:
    center_order = sorted(
        range(centers.shape[0]),
        key=lambda index: tuple(float(value) for value in centers[index]),
    )
    old_to_new = {old: new for new, old in enumerate(center_order)}
    canonical_labels = np.asarray([old_to_new[int(label)] for label in labels], dtype=int)
    canonical_centers = np.asarray(centers[center_order], dtype=float)
    return canonical_labels, canonical_centers


def _top_interests(
    samples: Sequence[FeatureSample], labels: np.ndarray, cluster_no: int
) -> list[str]:
    counts: Counter[str] = Counter()
    for sample, label in zip(samples, labels, strict=True):
        if int(label) == cluster_no:
            counts.update(set(sample.interests))
    return [
        interest
        for interest, _count in sorted(
            counts.items(), key=lambda item: (-item[1], item[0])
        )[:3]
    ]


def run_clustering(request: ClusteringRequest) -> ClusteringResponse:
    validate_request(request)
    ordered_samples = sorted(list(request.samples), key=lambda sample: sample.userId)
    preprocessed = preprocess_samples(ordered_samples)
    matrix = preprocessed.matrix

    distinct_feature_rows = int(np.unique(matrix, axis=0).shape[0])
    if distinct_feature_rows < request.clusterCount:
        raise clustering_computation_failed(
            reason="INSUFFICIENT_DISTINCT_FEATURE_ROWS"
        )

    try:
        estimator = KMeans(
            n_clusters=request.clusterCount,
            random_state=request.randomState,
            n_init=10,
            algorithm="lloyd",
        )
        original_labels = np.asarray(estimator.fit_predict(matrix), dtype=int)
        original_centers = np.asarray(estimator.cluster_centers_, dtype=float)
        inertia = float(estimator.inertia_)
    except (TypeError, ValueError, ArithmeticError) as error:
        raise clustering_computation_failed(reason="KMEANS_FAILED") from error

    if (
        original_labels.shape != (len(ordered_samples),)
        or original_centers.shape != (request.clusterCount, matrix.shape[1])
        or not np.isfinite(original_centers).all()
        or not np.isfinite(inertia)
        or inertia < 0.0
    ):
        raise clustering_computation_failed(reason="NON_FINITE_CLUSTERING_RESULT")

    labels, centers = _canonicalize_clusters(original_labels, original_centers)
    if set(int(label) for label in labels) != set(range(request.clusterCount)):
        raise clustering_computation_failed(
            reason="INSUFFICIENT_DISTINCT_FEATURE_ROWS"
        )

    distances = np.linalg.norm(matrix - centers[labels], axis=1)
    if not np.isfinite(distances).all() or np.any(distances < 0.0):
        raise clustering_computation_failed(reason="NON_FINITE_CLUSTERING_RESULT")

    coordinates, explained_variance_ratio = project_to_2d(matrix)
    communities = [
        CommunitySummary(
            clusterNo=cluster_no,
            memberCount=int(np.count_nonzero(labels == cluster_no)),
            topInterests=_top_interests(ordered_samples, labels, cluster_no),
        )
        for cluster_no in range(request.clusterCount)
    ]
    members = [
        MemberResult(
            userId=sample.userId,
            clusterNo=int(labels[index]),
            coordinateX=float(coordinates[index, 0]),
            coordinateY=float(coordinates[index, 1]),
            distanceToCenter=float(distances[index]),
        )
        for index, sample in enumerate(ordered_samples)
    ]

    return ClusteringResponse(
        runId=request.runId,
        version=request.version,
        algorithm="KMEANS",
        clusterCount=request.clusterCount,
        sampleCount=len(ordered_samples),
        metrics=ClusteringMetrics(
            inertia=inertia,
            pcaExplainedVarianceRatio=explained_variance_ratio,
        ),
        communities=communities,
        members=members,
    )

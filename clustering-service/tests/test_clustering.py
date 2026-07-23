from __future__ import annotations

import numpy as np
import pytest

from app import clustering
from app.clustering import (
    _canonicalize_clusters,
    _normalize_axis,
    _top_interests,
    project_to_2d,
    run_clustering,
)
from app.exceptions import ClusteringServiceError
from app.schemas import ClusteringRequest, FeatureSample


def make_sample(user_id: str, signup_count: int, interests: list[str]) -> dict[str, object]:
    return {
        "userId": user_id,
        "interests": interests,
        "college": "软件学院",
        "grade": "2024级",
        "availableTime": [],
        "signupCount": signup_count,
        "approvedSignupCount": 0,
        "favoriteCount": 0,
        "checkInCount": 0,
        "feedbackCount": 0,
        "averageRating": None,
        "categoryParticipationCounts": {},
    }


def request_for(signup_counts: list[int], cluster_count: int = 2) -> ClusteringRequest:
    return ClusteringRequest.model_validate(
        {
            "runId": "run-unit",
            "version": "cc-unit",
            "algorithm": "KMEANS",
            "clusterCount": cluster_count,
            "randomState": 42,
            "featureSchemaVersion": "community-features-v1",
            "samples": [
                make_sample(f"user-{index}", count, [f"interest-{index % 2}"])
                for index, count in enumerate(signup_counts)
            ],
        }
    )


def test_kmeans_returns_one_membership_per_user() -> None:
    result = run_clustering(request_for([1, 2, 8, 9]))

    assert len(result.members) == 4
    assert len({member.userId for member in result.members}) == 4
    assert {community.clusterNo for community in result.communities} == {0, 1}
    assert sum(community.memberCount for community in result.communities) == 4
    assert all(np.isfinite(member.distanceToCenter) for member in result.members)


def test_kmeans_is_deterministic() -> None:
    request = request_for([1, 2, 8, 9])
    first = run_clustering(request).model_dump()
    second = run_clustering(request).model_dump()
    assert first == second


def test_random_state_is_passed_to_kmeans(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    original_kmeans = clustering.KMeans
    captured_arguments: dict[str, object] = {}

    def recording_kmeans(**kwargs: object):
        captured_arguments.update(kwargs)
        return original_kmeans(**kwargs)

    monkeypatch.setattr(clustering, "KMeans", recording_kmeans)

    run_clustering(request_for([1, 2, 8, 9]))

    assert captured_arguments["random_state"] == 42
    assert captured_arguments["n_init"] == 10
    assert captured_arguments["algorithm"] == "lloyd"


def test_sample_permutations_do_not_change_logical_results() -> None:
    base_request = request_for([1, 2, 8, 9])
    sample_payloads = [sample.model_dump() for sample in base_request.samples]
    permutations = (
        [0, 1, 2, 3],
        [3, 2, 1, 0],
        [2, 0, 3, 1],
    )
    results = []

    for order in permutations:
        request = ClusteringRequest.model_validate(
            {
                **base_request.model_dump(exclude={"samples"}),
                "samples": [sample_payloads[index] for index in order],
            }
        )
        original_order = [sample.userId for sample in request.samples]
        result = run_clustering(request)
        assert [sample.userId for sample in request.samples] == original_order
        assert [member.userId for member in result.members] == sorted(original_order)
        results.append(result)

    reference = results[0]
    reference_members = {member.userId: member for member in reference.members}
    for result in results[1:]:
        assert [community.model_dump() for community in result.communities] == [
            community.model_dump() for community in reference.communities
        ]
        assert result.metrics.inertia == pytest.approx(reference.metrics.inertia)
        assert result.metrics.pcaExplainedVarianceRatio == pytest.approx(
            reference.metrics.pcaExplainedVarianceRatio
        )
        for member in result.members:
            expected = reference_members[member.userId]
            assert member.clusterNo == expected.clusterNo
            assert member.coordinateX == pytest.approx(expected.coordinateX)
            assert member.coordinateY == pytest.approx(expected.coordinateY)
            assert member.distanceToCenter == pytest.approx(
                expected.distanceToCenter
            )


def test_canonicalization_is_stable_under_label_and_center_permutation() -> None:
    labels = np.asarray([0, 1, 0, 1], dtype=int)
    centers = np.asarray([[10.0, 5.0], [-10.0, -5.0]], dtype=float)
    canonical_labels, canonical_centers = _canonicalize_clusters(labels, centers)

    permuted_labels = np.asarray([1, 0, 1, 0], dtype=int)
    permuted_centers = centers[[1, 0]]
    permuted_canonical_labels, permuted_canonical_centers = _canonicalize_clusters(
        permuted_labels, permuted_centers
    )

    assert np.array_equal(canonical_labels, permuted_canonical_labels)
    assert np.array_equal(canonical_centers, permuted_canonical_centers)


def test_distance_to_center_uses_standardized_feature_space() -> None:
    request = request_for([0, 2, 8, 10])
    for sample in request.samples:
        sample.interests = []

    result = run_clustering(request)

    expected_distance = 1.0 / np.sqrt(17.0)
    assert [member.distanceToCenter for member in result.members] == pytest.approx(
        [expected_distance] * 4
    )


def test_single_effective_feature_still_returns_two_coordinates() -> None:
    request = request_for([1, 2, 8, 9])
    for sample in request.samples:
        sample.interests = []
    result = run_clustering(request)

    assert all(0.0 <= member.coordinateX <= 100.0 for member in result.members)
    assert all(member.coordinateY == 50.0 for member in result.members)
    assert result.metrics.pcaExplainedVarianceRatio[1] == 0.0


def test_near_zero_axis_range_is_centered() -> None:
    normalized = _normalize_axis(
        np.asarray([1.0, 1.0 + 1e-14, 1.0 + 2e-14], dtype=float)
    )
    assert np.all(normalized == 50.0)


def test_exact_zero_axis_range_is_centered() -> None:
    normalized = _normalize_axis(np.asarray([3.0, 3.0, 3.0], dtype=float))
    assert np.all(normalized == 50.0)


def test_axis_range_just_above_tolerance_is_normalized() -> None:
    documented_tolerance_at_unit_scale = 1e-12 + 1e-9
    span = documented_tolerance_at_unit_scale * 1.01
    normalized = _normalize_axis(
        np.asarray([1.0, 1.0 + span / 2.0, 1.0 + span], dtype=float)
    )
    assert normalized == pytest.approx([0.0, 50.0, 100.0])


def test_pca_zero_variance_axis_is_centered() -> None:
    coordinates, ratios = project_to_2d(
        np.asarray([[-2.0, 0.0], [0.0, 0.0], [2.0, 0.0]])
    )
    assert np.all(coordinates[:, 1] == 50.0)
    assert ratios[1] == 0.0
    assert np.all((coordinates >= 0.0) & (coordinates <= 100.0))


def test_pca_fully_degenerate_matrix_uses_documented_defaults() -> None:
    coordinates, ratios = project_to_2d(np.zeros((3, 4), dtype=float))
    assert np.all(coordinates == 50.0)
    assert ratios == [0.0, 0.0]


def test_pca_rank_two_produces_two_finite_normalized_axes() -> None:
    coordinates, ratios = project_to_2d(
        np.asarray(
            [
                [-1.0, -1.0],
                [-1.0, 1.0],
                [1.0, -1.0],
                [1.0, 1.0],
            ]
        )
    )

    assert np.linalg.matrix_rank(coordinates - coordinates.mean(axis=0)) == 2
    assert np.isfinite(coordinates).all()
    assert np.all((coordinates >= 0.0) & (coordinates <= 100.0))
    assert np.min(coordinates, axis=0) == pytest.approx([0.0, 0.0])
    assert np.max(coordinates, axis=0) == pytest.approx([100.0, 100.0])
    assert ratios == pytest.approx([0.5, 0.5])


def test_non_finite_pca_output_returns_documented_reason(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class NonFinitePca:
        components_ = np.asarray([[1.0, 0.0], [0.0, 1.0]])
        explained_variance_ratio_ = np.asarray([1.0, 0.0])

        def fit_transform(self, matrix: np.ndarray) -> np.ndarray:
            return np.full((matrix.shape[0], 2), float("nan"))

    monkeypatch.setattr(
        clustering,
        "PCA",
        lambda **_kwargs: NonFinitePca(),
    )
    with pytest.raises(ClusteringServiceError) as captured:
        project_to_2d(np.asarray([[-1.0, 0.0], [1.0, 0.0]]))
    assert captured.value.code == "CLUSTERING_COMPUTATION_FAILED"
    assert captured.value.details["reason"] == "NON_FINITE_PCA_RESULT"


def test_distinct_feature_rows_less_than_k_is_rejected() -> None:
    request = request_for([1, 1, 2], cluster_count=3)
    for sample in request.samples:
        sample.interests = []
    with pytest.raises(ClusteringServiceError) as captured:
        run_clustering(request)
    assert captured.value.status_code == 422
    assert captured.value.details["reason"] == "INSUFFICIENT_DISTINCT_FEATURE_ROWS"


def test_top_interests_use_frequency_then_string_order() -> None:
    samples = [
        FeatureSample.model_validate(make_sample("u1", 1, ["摄影", "AI"])),
        FeatureSample.model_validate(make_sample("u2", 2, ["AI", "编程"])),
        FeatureSample.model_validate(make_sample("u3", 3, ["编程", "摄影", "羽毛球"])),
    ]
    labels = np.asarray([0, 0, 0])
    assert _top_interests(samples, labels, 0) == ["AI", "摄影", "编程"]

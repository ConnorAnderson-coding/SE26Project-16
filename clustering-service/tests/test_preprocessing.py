from __future__ import annotations

import numpy as np
import pytest

from app.exceptions import ClusteringServiceError
from app.preprocessing import (
    ensure_finite_matrix,
    preprocess_samples,
    standardize_matrix,
)
from app.schemas import FeatureSample


def make_sample(
    user_id: str,
    *,
    signup_count: int,
    interests: list[str] | None = None,
    available_time: list[str] | None = None,
    categories: dict[str, int] | None = None,
) -> FeatureSample:
    return FeatureSample(
        userId=user_id,
        interests=interests or [],
        college="软件学院",
        grade="2024级",
        availableTime=available_time or [],
        signupCount=signup_count,
        approvedSignupCount=0,
        favoriteCount=0,
        checkInCount=0,
        feedbackCount=0,
        averageRating=None,
        categoryParticipationCounts=categories or {},
    )


def test_preprocessing_is_finite_and_standardized() -> None:
    result = preprocess_samples(
        [
            make_sample("u1", signup_count=1, interests=["AI"]),
            make_sample("u2", signup_count=3, interests=["摄影"]),
            make_sample("u3", signup_count=5, interests=[]),
        ]
    )

    assert result.matrix.shape[0] == 3
    assert result.matrix.shape[1] == len(result.feature_names)
    assert np.isfinite(result.matrix).all()
    assert np.allclose(result.matrix.mean(axis=0), 0.0, atol=1e-12)


def test_category_and_multivalue_order_do_not_change_encoding() -> None:
    first = make_sample(
        "u1",
        signup_count=2,
        interests=["摄影", "AI"],
        available_time=["weekend", "weekday_evening"],
        categories={"sports": 1, "academic": 2},
    )
    reordered = make_sample(
        "u1",
        signup_count=2,
        interests=["AI", "摄影"],
        available_time=["weekday_evening", "weekend"],
        categories={"academic": 2, "sports": 1},
    )
    reference = make_sample("u2", signup_count=7, interests=["羽毛球"])

    original_result = preprocess_samples([first, reference])
    reordered_result = preprocess_samples([reordered, reference])

    assert original_result.feature_names == reordered_result.feature_names
    assert np.array_equal(original_result.matrix, reordered_result.matrix)


def test_sparse_category_count_keys_are_aligned_by_union() -> None:
    result = preprocess_samples(
        [
            make_sample("u1", signup_count=1, categories={"academic": 2}),
            make_sample("u2", signup_count=2, categories={"sports": 3}),
        ]
    )
    assert "categoryParticipation:academic" in result.feature_names
    assert "categoryParticipation:sports" in result.feature_names
    assert np.isfinite(result.matrix).all()


def test_single_effective_feature_is_supported() -> None:
    result = preprocess_samples(
        [
            make_sample("u1", signup_count=1),
            make_sample("u2", signup_count=2),
            make_sample("u3", signup_count=3),
        ]
    )
    varying_columns = np.count_nonzero(np.var(result.matrix, axis=0) > 0.0)
    assert varying_columns == 1


def test_v2_uses_log_counts_and_profile_group_weights() -> None:
    samples = [
        make_sample("u1", signup_count=0),
        make_sample("u2", signup_count=3),
        make_sample("u3", signup_count=15),
    ]
    samples[0].college = "A"
    samples[1].college = "B"
    samples[2].college = "B"

    result = preprocess_samples(samples)

    signup_index = result.feature_names.index("numeric:logSignupCount")
    expected_signup = standardize_matrix(
        [[np.log1p(0)], [np.log1p(3)], [np.log1p(15)]]
    )[:, 0]
    assert np.allclose(result.matrix[:, signup_index], expected_signup)

    college_index = result.feature_names.index("college:A")
    expected_college = standardize_matrix([[1.0], [0.0], [0.0]])[:, 0] * 0.35
    assert np.allclose(result.matrix[:, college_index], expected_college)


def test_v2_derives_rates_category_proportions_and_rating_presence() -> None:
    first = make_sample(
        "u1", signup_count=4, categories={"academic": 1, "sports": 3}
    )
    first.approvedSignupCount = 2
    first.checkInCount = 1
    first.averageRating = 4.5
    first.feedbackCount = 1
    second = make_sample(
        "u2", signup_count=4, categories={"academic": 3, "sports": 1}
    )
    second.approvedSignupCount = 4
    second.checkInCount = 8

    result = preprocess_samples([first, second])

    assert result.feature_names[:8] == (
        "numeric:logSignupCount",
        "numeric:approvedRate",
        "numeric:logFavoriteCount",
        "numeric:logCheckInCount",
        "numeric:attendanceRate",
        "numeric:logFeedbackCount",
        "numeric:averageRating",
        "numeric:hasAverageRating",
    )
    approved_rate = result.matrix[:, result.feature_names.index("numeric:approvedRate")]
    attendance_rate = result.matrix[:, result.feature_names.index("numeric:attendanceRate")]
    academic = result.matrix[:, result.feature_names.index("categoryParticipation:academic")]
    rating_presence = result.matrix[:, result.feature_names.index("numeric:hasAverageRating")]
    assert np.allclose(approved_rate, [-1.0, 1.0])
    assert np.allclose(attendance_rate, [-1.0, 1.0])
    assert np.allclose(academic, [-1.0, 1.0])
    assert np.allclose(rating_presence, [1.0, -1.0])


def test_inconsistent_feature_dimensions_are_rejected() -> None:
    with pytest.raises(ClusteringServiceError) as captured:
        standardize_matrix([[1.0, 2.0], [3.0]])
    assert captured.value.code == "INVALID_SAMPLE_DATA"
    assert captured.value.details["reason"] == "INCONSISTENT_FEATURE_DIMENSIONS"


@pytest.mark.parametrize(
    "matrix",
    [
        [[1.0, float("nan")]],
        [[1.0, float("inf")]],
        [[1.0, float("-inf")]],
    ],
)
def test_non_finite_feature_values_are_rejected(matrix: list[list[float]]) -> None:
    with pytest.raises(ClusteringServiceError) as captured:
        ensure_finite_matrix(matrix)
    assert captured.value.code == "INVALID_SAMPLE_DATA"
    assert captured.value.details["reason"] == "NON_FINITE_FEATURE_VALUE"


def test_feature_row_count_must_match_sample_count() -> None:
    with pytest.raises(ClusteringServiceError) as captured:
        ensure_finite_matrix([[1.0], [2.0]], expected_rows=3)
    assert captured.value.details["reason"] == "INCONSISTENT_FEATURE_DIMENSIONS"


@pytest.mark.parametrize(
    "invalid_value",
    [10**400, "not-a-number", object()],
    ids=["overflow", "value-error", "type-error"],
)
@pytest.mark.parametrize("location", ["numeric", "category"])
def test_preprocessing_converts_numeric_conversion_errors_to_sample_errors(
    invalid_value: object, location: str
) -> None:
    sample = make_sample("u1", signup_count=1, categories={"academic": 1})
    if location == "numeric":
        sample.signupCount = invalid_value  # type: ignore[assignment]
    else:
        sample.categoryParticipationCounts["academic"] = invalid_value  # type: ignore[assignment]

    with pytest.raises(ClusteringServiceError) as captured:
        preprocess_samples([sample])

    assert captured.value.status_code == 400
    assert captured.value.code == "INVALID_SAMPLE_DATA"
    assert captured.value.details["reason"] == "INVALID_NUMERIC_FEATURE_VALUE"

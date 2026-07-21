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

"""Deterministic feature encoding and standardization."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler

from .exceptions import invalid_sample_data
from .schemas import FeatureSample


FeatureKey = tuple[str, str | None]
PROFILE_GROUP_WEIGHT = 0.35


@dataclass(frozen=True)
class PreprocessedData:
    matrix: np.ndarray
    feature_names: tuple[str, ...]


def _to_feature_float(value: object) -> float:
    try:
        return float(value)
    except (OverflowError, TypeError, ValueError) as error:
        raise invalid_sample_data(
            reason="INVALID_NUMERIC_FEATURE_VALUE",
            message="计数特征必须是可转换为有限浮点数的非负 int32 整数",
        ) from error


def ensure_finite_matrix(
    matrix: object, *, expected_rows: int | None = None
) -> np.ndarray:
    try:
        numeric_matrix = np.asarray(matrix, dtype=float)
    except (OverflowError, TypeError, ValueError) as error:
        raise invalid_sample_data(
            reason="INCONSISTENT_FEATURE_DIMENSIONS",
            message="特征列数量不一致或包含非数值字段",
        ) from error

    if numeric_matrix.ndim != 2 or numeric_matrix.shape[1] == 0:
        raise invalid_sample_data(
            reason="INCONSISTENT_FEATURE_DIMENSIONS",
            message="特征矩阵必须是非空二维矩阵",
        )
    if expected_rows is not None and numeric_matrix.shape[0] != expected_rows:
        raise invalid_sample_data(
            reason="INCONSISTENT_FEATURE_DIMENSIONS",
            details={"expectedRows": expected_rows, "actualRows": numeric_matrix.shape[0]},
            message="特征行数量与样本数量不一致",
        )
    if not np.isfinite(numeric_matrix).all():
        raise invalid_sample_data(
            reason="NON_FINITE_FEATURE_VALUE",
            message="特征数据不得包含 NaN 或无穷大",
        )
    return numeric_matrix


def standardize_matrix(matrix: object, *, expected_rows: int | None = None) -> np.ndarray:
    numeric_matrix = ensure_finite_matrix(matrix, expected_rows=expected_rows)
    try:
        standardized = StandardScaler().fit_transform(numeric_matrix)
    except (OverflowError, TypeError, ValueError) as error:
        raise invalid_sample_data(
            reason="FEATURE_STANDARDIZATION_FAILED",
            message="特征数据无法完成标准化",
        ) from error
    return ensure_finite_matrix(standardized, expected_rows=numeric_matrix.shape[0])


def _sorted_union(groups: Sequence[Sequence[str]]) -> list[str]:
    return sorted({value for group in groups for value in group})


def _single_value_categories(samples: Sequence[FeatureSample], field: str) -> list[str | None]:
    values = [getattr(sample, field) for sample in samples]
    categories: list[str | None] = sorted({value for value in values if value is not None})
    if any(value is None for value in values):
        categories.insert(0, None)
    return categories


def _display_feature_name(key: FeatureKey) -> str:
    namespace, value = key
    return f"{namespace}:{'<missing>' if value is None else value}"


def preprocess_samples(samples: Sequence[FeatureSample]) -> PreprocessedData:
    if not samples:
        raise invalid_sample_data(
            reason="EMPTY_SAMPLES",
            message="用户样本列表不能为空",
        )

    numeric_fields = (
        "logSignupCount",
        "approvedRate",
        "logFavoriteCount",
        "logCheckInCount",
        "attendanceRate",
        "logFeedbackCount",
        "averageRating",
        "hasAverageRating",
    )
    columns: list[FeatureKey] = [("numeric", field) for field in numeric_fields]

    college_categories = _single_value_categories(samples, "college")
    grade_categories = _single_value_categories(samples, "grade")
    interest_categories = _sorted_union([sample.interests for sample in samples])
    time_categories = _sorted_union([sample.availableTime for sample in samples])
    participation_categories = sorted(
        {
            category
            for sample in samples
            for category in sample.categoryParticipationCounts
        }
    )

    columns.extend(("college", value) for value in college_categories)
    columns.extend(("grade", value) for value in grade_categories)
    columns.extend(("interest", value) for value in interest_categories)
    columns.extend(("availableTime", value) for value in time_categories)
    columns.extend(("categoryParticipation", value) for value in participation_categories)

    records: list[dict[FeatureKey, float]] = []
    for sample in samples:
        signup_count = _to_feature_float(sample.signupCount)
        approved_count = _to_feature_float(sample.approvedSignupCount)
        check_in_count = _to_feature_float(sample.checkInCount)
        category_total = sum(
            _to_feature_float(count)
            for count in sample.categoryParticipationCounts.values()
        )
        row: dict[FeatureKey, float] = {
            ("numeric", "logSignupCount"): float(np.log1p(signup_count)),
            ("numeric", "approvedRate"): (
                approved_count / signup_count if signup_count > 0.0 else 0.0
            ),
            ("numeric", "logFavoriteCount"): float(
                np.log1p(_to_feature_float(sample.favoriteCount))
            ),
            ("numeric", "logCheckInCount"): float(np.log1p(check_in_count)),
            ("numeric", "attendanceRate"): (
                min(check_in_count / approved_count, 1.0)
                if approved_count > 0.0
                else 0.0
            ),
            ("numeric", "logFeedbackCount"): float(
                np.log1p(_to_feature_float(sample.feedbackCount))
            ),
            ("numeric", "averageRating"): _to_feature_float(
                sample.averageRating or 0.0
            ),
            ("numeric", "hasAverageRating"): _to_feature_float(
                sample.averageRating is not None
            ),
        }
        row[("college", sample.college)] = 1.0
        row[("grade", sample.grade)] = 1.0
        for interest in set(sample.interests):
            row[("interest", interest)] = 1.0
        for available_time in set(sample.availableTime):
            row[("availableTime", available_time)] = 1.0
        for category, count in sample.categoryParticipationCounts.items():
            numeric_count = _to_feature_float(count)
            row[("categoryParticipation", category)] = (
                numeric_count / category_total if category_total > 0.0 else 0.0
            )
        records.append(row)

    frame = pd.DataFrame.from_records(records)
    frame = frame.reindex(columns=columns, fill_value=0.0).fillna(0.0)
    try:
        raw_matrix = frame.to_numpy(dtype=float, copy=True)
    except (OverflowError, TypeError, ValueError) as error:
        raise invalid_sample_data(
            reason="INCONSISTENT_FEATURE_DIMENSIONS",
            message="特征数据无法转换为一致的数值矩阵",
        ) from error

    standardized = standardize_matrix(raw_matrix, expected_rows=len(samples))
    weights = np.asarray(
        [
            PROFILE_GROUP_WEIGHT if namespace in {"college", "grade"} else 1.0
            for namespace, _value in columns
        ],
        dtype=float,
    )
    standardized = ensure_finite_matrix(
        standardized * weights,
        expected_rows=len(samples),
    )
    return PreprocessedData(
        matrix=standardized,
        feature_names=tuple(_display_feature_name(column) for column in columns),
    )

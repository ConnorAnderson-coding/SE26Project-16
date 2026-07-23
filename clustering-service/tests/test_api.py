from __future__ import annotations

import json
import math

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app import clustering
from app.exceptions import _to_json_safe_value, clustering_computation_failed
from app.main import app
from app.schemas import INT32_MAX, FeatureSample


client = TestClient(app, raise_server_exceptions=False)
COUNT_FIELDS = (
    "signupCount",
    "approvedSignupCount",
    "favoriteCount",
    "checkInCount",
    "feedbackCount",
    "categoryParticipationCounts.academic",
)


def sample(
    user_id: str,
    *,
    interests: list[str],
    college: str,
    grade: str,
    signup_count: int,
    approved_count: int,
    favorite_count: int,
    check_in_count: int,
    feedback_count: int,
    average_rating: float | None,
    academic_count: int,
    sports_count: int,
) -> dict[str, object]:
    return {
        "userId": user_id,
        "interests": interests,
        "college": college,
        "grade": grade,
        "availableTime": ["weekend"] if sports_count else ["weekday_evening"],
        "signupCount": signup_count,
        "approvedSignupCount": approved_count,
        "favoriteCount": favorite_count,
        "checkInCount": check_in_count,
        "feedbackCount": feedback_count,
        "averageRating": average_rating,
        "categoryParticipationCounts": {
            "academic": academic_count,
            "sports": sports_count,
        },
    }


def valid_samples() -> list[dict[str, object]]:
    return [
        sample(
            "user-1",
            interests=["AI", "编程"],
            college="软件学院",
            grade="2024级",
            signup_count=8,
            approved_count=7,
            favorite_count=6,
            check_in_count=6,
            feedback_count=5,
            average_rating=4.8,
            academic_count=7,
            sports_count=0,
        ),
        sample(
            "user-2",
            interests=["AI", "摄影"],
            college="软件学院",
            grade="2024级",
            signup_count=7,
            approved_count=6,
            favorite_count=5,
            check_in_count=5,
            feedback_count=4,
            average_rating=4.5,
            academic_count=6,
            sports_count=0,
        ),
        sample(
            "user-3",
            interests=["羽毛球"],
            college="体育学院",
            grade="2023级",
            signup_count=5,
            approved_count=4,
            favorite_count=2,
            check_in_count=4,
            feedback_count=1,
            average_rating=4.0,
            academic_count=0,
            sports_count=4,
        ),
        sample(
            "user-4",
            interests=["篮球"],
            college="体育学院",
            grade="2023级",
            signup_count=4,
            approved_count=4,
            favorite_count=1,
            check_in_count=3,
            feedback_count=0,
            average_rating=None,
            academic_count=0,
            sports_count=4,
        ),
    ]


def payload(
    *, samples: list[dict[str, object]] | None = None, cluster_count: int = 2
) -> dict[str, object]:
    return {
        "runId": "run-001",
        "version": "cc-test-001",
        "algorithm": "KMEANS",
        "clusterCount": cluster_count,
        "randomState": 42,
        "featureSchemaVersion": "community-features-v2",
        "samples": valid_samples() if samples is None else samples,
    }


def assert_error_structure(response, *, status: int, code: str) -> dict[str, object]:
    assert response.status_code == status
    body = response.json()
    assert set(body) == {"code", "message", "details"}
    assert body["code"] == code
    assert isinstance(body["message"], str) and body["message"]
    assert isinstance(body["details"], dict)
    return body


def assert_standard_json_response(response) -> dict[str, object]:
    def reject_non_standard_constant(value: str) -> None:
        raise AssertionError(f"响应包含非标准 JSON 数字值: {value}")

    body = json.loads(
        response.text,
        parse_constant=reject_non_standard_constant,
    )
    assert isinstance(body, dict)
    return body


def post_raw_json(request_body: dict[str, object]):
    raw_body = json.dumps(
        request_body,
        allow_nan=True,
        ensure_ascii=False,
    ).encode("utf-8")
    return client.post(
        "/internal/v1/clustering/run",
        content=raw_body,
        headers={"Content-Type": "application/json"},
    )


def set_count_field(
    sample_data: dict[str, object], field: str, value: object
) -> None:
    if field.startswith("categoryParticipationCounts."):
        category = field.split(".", maxsplit=1)[1]
        counts = sample_data["categoryParticipationCounts"]
        assert isinstance(counts, dict)
        counts[category] = value
        return

    sample_data[field] = value
    if field == "signupCount" and isinstance(value, int) and not isinstance(value, bool):
        sample_data["approvedSignupCount"] = min(
            int(sample_data["approvedSignupCount"]), value
        )
    if (
        field == "approvedSignupCount"
        and isinstance(value, int)
        and not isinstance(value, bool)
    ):
        sample_data["signupCount"] = max(int(sample_data["signupCount"]), value)


def test_run_clustering_success_and_contract() -> None:
    response = client.post("/internal/v1/clustering/run", json=payload())

    assert response.status_code == 200
    body = response.json()
    assert body["runId"] == "run-001"
    assert body["version"] == "cc-test-001"
    assert body["algorithm"] == "KMEANS"
    assert body["clusterCount"] == 2
    assert body["sampleCount"] == 4
    assert len(body["communities"]) == 2
    assert len(body["members"]) == 4
    assert {item["clusterNo"] for item in body["communities"]} == {0, 1}
    assert sum(item["memberCount"] for item in body["communities"]) == 4

    user_ids = [item["userId"] for item in body["members"]]
    assert user_ids == ["user-1", "user-2", "user-3", "user-4"]
    assert len(user_ids) == len(set(user_ids))
    for member in body["members"]:
        assert 0.0 <= member["coordinateX"] <= 100.0
        assert 0.0 <= member["coordinateY"] <= 100.0
        assert member["distanceToCenter"] >= 0.0
        assert all(
            math.isfinite(member[field])
            for field in ("coordinateX", "coordinateY", "distanceToCenter")
        )


def test_same_request_is_deterministic() -> None:
    first = client.post("/internal/v1/clustering/run", json=payload())
    second = client.post("/internal/v1/clustering/run", json=payload())

    assert first.status_code == second.status_code == 200
    assert first.json() == second.json()


def test_random_state_is_required() -> None:
    request_body = payload()
    request_body.pop("randomState")
    response = client.post("/internal/v1/clustering/run", json=request_body)
    assert_error_structure(response, status=400, code="INVALID_SAMPLE_DATA")


def test_random_state_42_is_accepted() -> None:
    response = client.post("/internal/v1/clustering/run", json=payload())
    assert response.status_code == 200


def test_random_state_other_than_42_is_rejected() -> None:
    request_body = payload()
    request_body["randomState"] = 7
    response = client.post("/internal/v1/clustering/run", json=request_body)
    body = assert_error_structure(
        response, status=400, code="INVALID_SAMPLE_DATA"
    )
    assert body["details"]["reason"] == "INVALID_RANDOM_STATE"


@pytest.mark.parametrize("field", COUNT_FIELDS)
@pytest.mark.parametrize("boundary", [0, INT32_MAX])
def test_count_fields_accept_int32_boundaries(field: str, boundary: int) -> None:
    samples = valid_samples()
    set_count_field(samples[0], field, boundary)

    response = client.post(
        "/internal/v1/clustering/run", json=payload(samples=samples)
    )

    assert response.status_code == 200


@pytest.mark.parametrize("field", COUNT_FIELDS)
@pytest.mark.parametrize(
    "invalid_value",
    [INT32_MAX + 1, 10**400, True, 1.5, "1"],
    ids=["above-int32", "huge-integer", "boolean", "float", "string"],
)
def test_count_fields_reject_non_int32_values(
    field: str, invalid_value: object
) -> None:
    samples = valid_samples()
    set_count_field(samples[0], field, invalid_value)

    response = client.post(
        "/internal/v1/clustering/run", json=payload(samples=samples)
    )

    assert_error_structure(response, status=400, code="INVALID_SAMPLE_DATA")


def test_success_response_does_not_expose_forbidden_fields() -> None:
    response = client.post("/internal/v1/clustering/run", json=payload())
    assert response.status_code == 200

    forbidden = {
        "communityId",
        "pointId",
        "name",
        "description",
        "color",
        "password",
        "token",
        "serviceUrl",
        "featureVector",
        "rawFeatures",
        "standardizedMatrix",
    }

    def collect_keys(value: object) -> set[str]:
        if isinstance(value, dict):
            return set(value).union(*(collect_keys(item) for item in value.values()))
        if isinstance(value, list):
            return set().union(*(collect_keys(item) for item in value)) if value else set()
        return set()

    assert collect_keys(response.json()).isdisjoint(forbidden)


def test_empty_samples_returns_invalid_sample_data() -> None:
    response = client.post(
        "/internal/v1/clustering/run", json=payload(samples=[])
    )
    body = assert_error_structure(
        response, status=400, code="INVALID_SAMPLE_DATA"
    )
    assert body["details"]["reason"] == "INSUFFICIENT_SAMPLES"


@pytest.mark.parametrize("cluster_count", [0, 1, 5])
def test_invalid_cluster_count(cluster_count: int) -> None:
    response = client.post(
        "/internal/v1/clustering/run", json=payload(cluster_count=cluster_count)
    )
    assert_error_structure(response, status=400, code="INVALID_CLUSTER_COUNT")


def test_non_integer_cluster_count_uses_cluster_count_error() -> None:
    request_body = payload()
    request_body["clusterCount"] = "2"
    response = client.post("/internal/v1/clustering/run", json=request_body)
    assert_error_structure(response, status=400, code="INVALID_CLUSTER_COUNT")


@pytest.mark.parametrize(
    ("invalid_value", "expected_actual"),
    [
        (float("nan"), "NaN"),
        (float("inf"), "Infinity"),
        (float("-inf"), "-Infinity"),
    ],
    ids=["nan", "positive-infinity", "negative-infinity"],
)
def test_non_finite_cluster_count_returns_json_safe_400(
    invalid_value: float, expected_actual: str
) -> None:
    request_body = payload()
    request_body["clusterCount"] = invalid_value

    response = post_raw_json(request_body)

    body = assert_error_structure(
        response, status=400, code="INVALID_CLUSTER_COUNT"
    )
    assert body["message"] == "聚类数量必须为整数并满足样本数量范围"
    assert body["details"]["actual"] == expected_actual
    assert assert_standard_json_response(response) == body


@pytest.mark.parametrize(
    "invalid_value",
    [float("nan"), float("inf")],
    ids=["nan", "infinity"],
)
def test_non_finite_random_state_returns_json_safe_400(
    invalid_value: float,
) -> None:
    request_body = payload()
    request_body["randomState"] = invalid_value

    response = post_raw_json(request_body)

    body = assert_error_structure(
        response, status=400, code="INVALID_SAMPLE_DATA"
    )
    assert assert_standard_json_response(response) == body
    assert any(
        error["field"] == "randomState" for error in body["details"]["errors"]
    )


def test_non_finite_sample_count_returns_json_safe_400() -> None:
    samples = valid_samples()
    samples[0]["signupCount"] = float("nan")

    response = post_raw_json(payload(samples=samples))

    body = assert_error_structure(
        response, status=400, code="INVALID_SAMPLE_DATA"
    )
    assert assert_standard_json_response(response) == body
    assert any(
        error["field"] == "samples.0.signupCount"
        for error in body["details"]["errors"]
    )


def test_json_safe_conversion_recurses_through_error_details() -> None:
    class OpaqueValue:
        def __str__(self) -> str:
            return "opaque-value"

    class UnstringableValue:
        def __str__(self) -> str:
            raise RuntimeError("cannot stringify")

    details = {
        "nested": {
            "list": [float("nan"), {"value": float("inf")}],
            "tuple": (float("-inf"), 1.5, None, True),
            "set": {"item", 2},
            float("inf"): float("-inf"),
        },
        "opaque": OpaqueValue(),
        "unstringable": UnstringableValue(),
    }

    safe_details = _to_json_safe_value(details)

    assert isinstance(safe_details, dict)
    nested = safe_details["nested"]
    assert isinstance(nested, dict)
    assert nested["list"] == ["NaN", {"value": "Infinity"}]
    assert nested["tuple"] == ["-Infinity", 1.5, None, True]
    assert set(nested["set"]) == {"item", 2}
    assert nested["Infinity"] == "-Infinity"
    assert safe_details["opaque"] == "opaque-value"
    assert safe_details["unstringable"] == "<unserializable UnstringableValue>"
    json.dumps(safe_details, allow_nan=False)


def test_duplicate_user_id_is_explicit_input_error() -> None:
    samples = valid_samples()
    samples[1]["userId"] = samples[0]["userId"]
    response = client.post(
        "/internal/v1/clustering/run", json=payload(samples=samples)
    )
    body = assert_error_structure(
        response, status=400, code="INVALID_SAMPLE_DATA"
    )
    assert body["details"]["reason"] == "DUPLICATE_USER_ID"


@pytest.mark.parametrize("invalid_value", [float("nan"), float("inf"), float("-inf")])
def test_non_finite_rating_is_rejected_by_pydantic(invalid_value: float) -> None:
    sample_data = valid_samples()[0]
    sample_data["averageRating"] = invalid_value

    with pytest.raises(ValidationError) as captured:
        FeatureSample.model_validate(sample_data)

    assert any(
        error["loc"] == ("averageRating",) and error["type"] == "finite_number"
        for error in captured.value.errors()
    )


@pytest.mark.parametrize("invalid_value", [float("nan"), float("inf"), float("-inf")])
def test_non_finite_rating_reaches_api_and_is_rejected(invalid_value: float) -> None:
    samples = valid_samples()
    samples[0]["averageRating"] = invalid_value
    raw_body = json.dumps(
        payload(samples=samples),
        allow_nan=True,
        ensure_ascii=False,
    ).encode("utf-8")

    response = client.post(
        "/internal/v1/clustering/run",
        content=raw_body,
        headers={"Content-Type": "application/json"},
    )
    body = assert_error_structure(
        response, status=400, code="INVALID_SAMPLE_DATA"
    )
    assert any(
        error["field"] == "samples.0.averageRating"
        and error["reason"] == "finite_number"
        for error in body["details"]["errors"]
    )


def test_malformed_feature_shape_is_invalid_sample_data() -> None:
    samples = valid_samples()
    samples[0]["categoryParticipationCounts"] = [1, 2, 3]
    response = client.post(
        "/internal/v1/clustering/run", json=payload(samples=samples)
    )
    assert_error_structure(response, status=400, code="INVALID_SAMPLE_DATA")


def test_insufficient_distinct_feature_rows_returns_computation_error() -> None:
    repeated = valid_samples()[0]
    samples = [{**repeated, "userId": f"user-{index}"} for index in range(3)]
    response = client.post(
        "/internal/v1/clustering/run",
        json=payload(samples=samples, cluster_count=2),
    )
    body = assert_error_structure(
        response, status=422, code="CLUSTERING_COMPUTATION_FAILED"
    )
    assert body["details"]["reason"] == "INSUFFICIENT_DISTINCT_FEATURE_ROWS"


def test_unsupported_feature_schema_returns_409() -> None:
    request_body = payload()
    request_body["featureSchemaVersion"] = "community-features-v1"
    response = client.post("/internal/v1/clustering/run", json=request_body)
    assert_error_structure(response, status=409, code="INVALID_FEATURE_SCHEMA")


def test_non_finite_pca_result_uses_documented_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def fail_projection(_matrix: object):
        raise clustering_computation_failed(reason="NON_FINITE_PCA_RESULT")

    monkeypatch.setattr(clustering, "project_to_2d", fail_projection)
    response = client.post("/internal/v1/clustering/run", json=payload())
    body = assert_error_structure(
        response, status=422, code="CLUSTERING_COMPUTATION_FAILED"
    )
    assert body["details"]["reason"] == "NON_FINITE_PCA_RESULT"


def test_health_contract_does_not_expose_configuration() -> None:
    response = client.get("/internal/v1/health")
    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "clustering-service",
        "supportedFeatureSchemas": ["community-features-v2"],
    }


def test_documentation_routes_are_intentionally_disabled() -> None:
    response = client.get("/docs")
    assert_error_structure(response, status=404, code="INVALID_SAMPLE_DATA")


def test_request_validation_uses_unified_error_structure() -> None:
    response = client.post(
        "/internal/v1/clustering/run",
        json={"runId": "run-only", "clusterCount": 2},
    )
    assert_error_structure(response, status=400, code="INVALID_SAMPLE_DATA")

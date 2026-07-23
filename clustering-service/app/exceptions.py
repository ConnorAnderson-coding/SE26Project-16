"""Domain exceptions and safe FastAPI error responses."""

from __future__ import annotations

import math
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from .schemas import ErrorResponse


class ClusteringServiceError(Exception):
    def __init__(
        self,
        *,
        status_code: int,
        code: str,
        message: str,
        details: dict[str, object] | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message
        self.details = details or {}


def invalid_sample_data(
    *,
    reason: str,
    details: dict[str, object] | None = None,
    message: str = "样本数据无效",
) -> ClusteringServiceError:
    safe_details = {"reason": reason}
    if details:
        safe_details.update(details)
    return ClusteringServiceError(
        status_code=400,
        code="INVALID_SAMPLE_DATA",
        message=message,
        details=safe_details,
    )


def invalid_cluster_count(*, actual: int, sample_count: int) -> ClusteringServiceError:
    return ClusteringServiceError(
        status_code=400,
        code="INVALID_CLUSTER_COUNT",
        message=f"聚类数量必须在 2 和样本数 {sample_count} 之间",
        details={"min": 2, "max": sample_count, "actual": actual},
    )


def invalid_feature_schema(*, actual: str) -> ClusteringServiceError:
    return ClusteringServiceError(
        status_code=409,
        code="INVALID_FEATURE_SCHEMA",
        message="不支持的特征模式版本",
        details={
            "actual": actual,
            "supported": ["community-features-v2"],
        },
    )


def clustering_computation_failed(*, reason: str) -> ClusteringServiceError:
    return ClusteringServiceError(
        status_code=422,
        code="CLUSTERING_COMPUTATION_FAILED",
        message="聚类计算无法产生有效结果",
        details={"reason": reason},
    )


def _to_json_safe_value(
    value: object, _seen_container_ids: set[int] | None = None
) -> object:
    """Recursively convert error details to values accepted by strict JSON."""
    if value is None or isinstance(value, (str, int, bool)):
        return value

    if isinstance(value, float):
        if math.isnan(value):
            return "NaN"
        if math.isinf(value):
            return "Infinity" if value > 0 else "-Infinity"
        return value

    if isinstance(value, (dict, list, tuple, set)):
        seen_container_ids = (
            set() if _seen_container_ids is None else _seen_container_ids
        )
        container_id = id(value)
        if container_id in seen_container_ids:
            return "<circular reference>"
        seen_container_ids.add(container_id)
        try:
            if isinstance(value, dict):
                safe_dict: dict[str, object] = {}
                for key, item in value.items():
                    safe_key_value = _to_json_safe_value(key, seen_container_ids)
                    if isinstance(safe_key_value, str):
                        safe_key = safe_key_value
                    elif safe_key_value is None:
                        safe_key = "null"
                    elif isinstance(safe_key_value, bool):
                        safe_key = "true" if safe_key_value else "false"
                    else:
                        safe_key = _safe_string(safe_key_value)
                    safe_dict[safe_key] = _to_json_safe_value(
                        item, seen_container_ids
                    )
                return safe_dict

            return [
                _to_json_safe_value(item, seen_container_ids) for item in value
            ]
        finally:
            seen_container_ids.remove(container_id)

    return _safe_string(value)


def _safe_string(value: object) -> str:
    try:
        return str(value)
    except Exception:
        return f"<unserializable {type(value).__name__}>"


def _error_content(
    *, code: str, message: str, details: dict[str, object]
) -> dict[str, Any]:
    safe_details = _to_json_safe_value(details)
    if not isinstance(safe_details, dict):
        safe_details = {}
    return ErrorResponse(
        code=code,
        message=message,
        details=safe_details,
    ).model_dump()


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(ClusteringServiceError)
    async def handle_clustering_error(
        _request: Request, error: ClusteringServiceError
    ) -> JSONResponse:
        return JSONResponse(
            status_code=error.status_code,
            content=_error_content(
                code=error.code,
                message=error.message,
                details=error.details,
            ),
        )

    @app.exception_handler(RequestValidationError)
    async def handle_request_validation_error(
        _request: Request, error: RequestValidationError
    ) -> JSONResponse:
        cluster_count_error = next(
            (
                item
                for item in error.errors()
                if item.get("loc") and item["loc"][-1] == "clusterCount"
            ),
            None,
        )
        if cluster_count_error is not None:
            body = error.body if isinstance(error.body, dict) else {}
            samples = body.get("samples")
            sample_count = len(samples) if isinstance(samples, list) else 0
            return JSONResponse(
                status_code=400,
                content=_error_content(
                    code="INVALID_CLUSTER_COUNT",
                    message="聚类数量必须为整数并满足样本数量范围",
                    details={
                        "min": 2,
                        "max": sample_count,
                        "actual": body.get("clusterCount"),
                    },
                ),
            )

        safe_errors: list[dict[str, str]] = []
        for item in error.errors():
            location = ".".join(
                str(part)
                for part in item.get("loc", ())
                if part not in {"body", "query", "path"}
            )
            safe_errors.append(
                {
                    "field": location or "request",
                    "reason": str(item.get("type", "validation_error")),
                }
            )
        return JSONResponse(
            status_code=400,
            content=_error_content(
                code="INVALID_SAMPLE_DATA",
                message="请求数据格式或字段值无效",
                details={"errors": safe_errors},
            ),
        )

    @app.exception_handler(StarletteHTTPException)
    async def handle_http_error(
        _request: Request, error: StarletteHTTPException
    ) -> JSONResponse:
        if error.status_code == 503:
            return JSONResponse(
                status_code=503,
                content=_error_content(
                    code="SERVICE_UNAVAILABLE",
                    message="聚类服务尚未就绪",
                    details={},
                ),
            )
        return JSONResponse(
            status_code=error.status_code,
            content=_error_content(
                code="INVALID_SAMPLE_DATA",
                message="请求路径或方法无效",
                details={},
            ),
        )

    @app.exception_handler(Exception)
    async def handle_unexpected_error(
        _request: Request, _error: Exception
    ) -> JSONResponse:
        return JSONResponse(
            status_code=500,
            content=_error_content(
                code="INTERNAL_ERROR",
                message="聚类服务发生未预期错误",
                details={},
            ),
        )

"""FastAPI entry point for the internal clustering service."""

from __future__ import annotations

from fastapi import FastAPI

from .clustering import SUPPORTED_FEATURE_SCHEMA, run_clustering
from .exceptions import register_exception_handlers
from .schemas import ClusteringRequest, ClusteringResponse, HealthResponse


app = FastAPI(
    title="Campus Community Clustering Service",
    version="1.0.0",
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)
register_exception_handlers(app)


@app.post(
    "/internal/v1/clustering/run",
    response_model=ClusteringResponse,
    status_code=200,
)
def execute_clustering(request: ClusteringRequest) -> ClusteringResponse:
    return run_clustering(request)


@app.get(
    "/internal/v1/health",
    response_model=HealthResponse,
    status_code=200,
)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        service="clustering-service",
        supportedFeatureSchemas=[SUPPORTED_FEATURE_SCHEMA],
    )

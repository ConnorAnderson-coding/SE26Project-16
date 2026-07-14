"""Pydantic request and response contracts for the internal clustering API."""

from __future__ import annotations

from typing import Annotated

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    FiniteFloat,
    StringConstraints,
    model_validator,
)


Identifier = Annotated[str, StringConstraints(min_length=1, max_length=64)]
Label = Annotated[str, StringConstraints(min_length=1)]
INT32_MAX = 2_147_483_647
NonNegativeInt32 = Annotated[int, Field(strict=True, ge=0, le=INT32_MAX)]
Coordinate = Annotated[FiniteFloat, Field(ge=0.0, le=100.0)]
Ratio = Annotated[FiniteFloat, Field(ge=0.0, le=1.0)]


class ContractModel(BaseModel):
    """Base model that rejects protocol fields not declared by the contract."""

    model_config = ConfigDict(extra="forbid", strict=True)


class FeatureSample(ContractModel):
    userId: Identifier
    interests: list[Label]
    college: Label | None = None
    grade: Label | None = None
    availableTime: list[Label]
    signupCount: NonNegativeInt32
    approvedSignupCount: NonNegativeInt32
    favoriteCount: NonNegativeInt32
    checkInCount: NonNegativeInt32
    feedbackCount: NonNegativeInt32
    averageRating: Annotated[
        FiniteFloat | None,
        Field(ge=1.0, le=5.0),
    ] = None
    categoryParticipationCounts: dict[Label, NonNegativeInt32]

    @model_validator(mode="after")
    def approved_count_cannot_exceed_signup_count(self) -> FeatureSample:
        if self.approvedSignupCount > self.signupCount:
            raise ValueError("approvedSignupCount must not exceed signupCount")
        return self


class ClusteringRequest(ContractModel):
    runId: Identifier
    version: Identifier
    algorithm: str
    clusterCount: Annotated[int, Field(strict=True)]
    randomState: Annotated[int, Field(strict=True)]
    featureSchemaVersion: str
    samples: list[FeatureSample]


class ClusteringMetrics(ContractModel):
    inertia: Annotated[FiniteFloat, Field(ge=0.0)]
    pcaExplainedVarianceRatio: Annotated[list[Ratio], Field(min_length=2, max_length=2)]


class CommunitySummary(ContractModel):
    clusterNo: NonNegativeInt32
    memberCount: Annotated[int, Field(strict=True, gt=0)]
    topInterests: Annotated[list[str], Field(max_length=3)]


class MemberResult(ContractModel):
    userId: Identifier
    clusterNo: NonNegativeInt32
    coordinateX: Coordinate
    coordinateY: Coordinate
    distanceToCenter: Annotated[FiniteFloat, Field(ge=0.0)]


class ClusteringResponse(ContractModel):
    runId: Identifier
    version: Identifier
    algorithm: str
    clusterCount: Annotated[int, Field(strict=True, ge=2)]
    sampleCount: Annotated[int, Field(strict=True, ge=2)]
    metrics: ClusteringMetrics
    communities: list[CommunitySummary]
    members: list[MemberResult]


class HealthResponse(ContractModel):
    status: str
    service: str
    supportedFeatureSchemas: list[str]


class ErrorResponse(ContractModel):
    code: str
    message: str
    details: dict[str, object]

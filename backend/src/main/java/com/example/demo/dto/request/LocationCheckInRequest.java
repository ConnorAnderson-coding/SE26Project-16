package com.example.demo.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LocationCheckInRequest {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度不合法")
    @DecimalMax(value = "90.0", message = "纬度不合法")
    private Double latitude;

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度不合法")
    @DecimalMax(value = "180.0", message = "经度不合法")
    private Double longitude;
}

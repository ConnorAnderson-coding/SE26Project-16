package com.example.demo.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ActivityRequest {

    @NotBlank(message = "活动名称不能为空")
    @Size(max = 200)
    private String title;

    @NotBlank(message = "活动类别不能为空")
    @Size(max = 32)
    private String category;

    @NotBlank(message = "活动内容不能为空")
    @Size(max = 5000)
    private String description;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotBlank(message = "活动地点不能为空")
    @Size(max = 200)
    private String location;

    @NotNull(message = "人数上限不能为空")
    @Min(value = 1, message = "人数上限至少为1")
    private Integer maxParticipants;

    @Size(max = 500)
    private String poster;

    @DecimalMin(value = "-90.0", message = "纬度不合法")
    @DecimalMax(value = "90.0", message = "纬度不合法")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "经度不合法")
    @DecimalMax(value = "180.0", message = "经度不合法")
    private Double longitude;

    @Min(value = 1, message = "签到半径至少为1米")
    private Integer checkInRadiusMeters;

    private List<String> tags;
}

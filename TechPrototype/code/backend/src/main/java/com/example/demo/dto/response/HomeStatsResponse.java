package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HomeStatsResponse {
    private long mySignupCount;
    private long approvedCount;
    private long myFavoriteCount;
}

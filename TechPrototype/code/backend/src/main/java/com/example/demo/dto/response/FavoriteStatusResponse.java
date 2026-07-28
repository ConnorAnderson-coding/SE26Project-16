package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FavoriteStatusResponse {
    private boolean favorited;
}

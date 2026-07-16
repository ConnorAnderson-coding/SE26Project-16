package com.example.demo.search.support;

import com.example.demo.dto.response.ActivityResponse;

public final class ActivityHotScore {

    private ActivityHotScore() {
    }

    public static int compute(ActivityResponse activity) {
        int signup = activity.getSignupCount() != null ? activity.getSignupCount() : 0;
        int favorite = activity.getFavoriteCount() != null ? activity.getFavoriteCount() : 0;
        return signup + favorite;
    }
}

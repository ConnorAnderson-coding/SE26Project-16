package com.example.demo.recommend.support;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Aligns with frontend {@code getActivityTimeSlot} / {@code matchesAvailableTime}.
 */
public final class ActivityTimeSlot {

    public static final String WEEKDAY_MORNING = "weekday_morning";
    public static final String WEEKDAY_AFTERNOON = "weekday_afternoon";
    public static final String WEEKDAY_EVENING = "weekday_evening";
    public static final String WEEKEND = "weekend";

    private ActivityTimeSlot() {
    }

    public static String fromStartTime(LocalDateTime start) {
        if (start == null) {
            return null;
        }
        DayOfWeek day = start.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return WEEKEND;
        }
        int hour = start.getHour();
        if (hour < 12) {
            return WEEKDAY_MORNING;
        }
        if (hour < 18) {
            return WEEKDAY_AFTERNOON;
        }
        return WEEKDAY_EVENING;
    }

    /**
     * @return 1 if fits (or user has no slots), 0 if conflicts with user's available_time
     */
    public static double timeFit(LocalDateTime start, List<String> userSlots) {
        if (userSlots == null || userSlots.isEmpty()) {
            return 1.0;
        }
        String slot = fromStartTime(start);
        if (slot == null) {
            return 0.0;
        }
        return userSlots.contains(slot) ? 1.0 : 0.0;
    }
}

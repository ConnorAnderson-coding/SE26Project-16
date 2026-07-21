package com.example.campusactivity.client.clustering.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class ClusteringContractChecks {
    private ClusteringContractChecks() {
    }

    static String identifier(String value, String field) {
        string(value, field);
        if (value.length() > 64) {
            throw new IllegalArgumentException(field + " 长度不能超过 64");
        }
        return value;
    }

    static String string(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白字符串");
        }
        return value;
    }

    static Integer requiredInteger(Integer value, String field) {
        return Objects.requireNonNull(value, field + " 不能为空");
    }

    static int nonNegative(Integer value, String field) {
        int checked = requiredInteger(value, field);
        if (checked < 0) {
            throw new IllegalArgumentException(field + " 不能为负数");
        }
        return checked;
    }

    static double finite(Double value, String field) {
        double checked = Objects.requireNonNull(value, field + " 不能为空");
        if (!Double.isFinite(checked)) {
            throw new IllegalArgumentException(field + " 必须为有限数");
        }
        return checked;
    }

    static List<String> stringList(List<String> values, String field, boolean requireNonEmptyItems) {
        Objects.requireNonNull(values, field + " 不能为空");
        List<String> copy = List.copyOf(values);
        if (requireNonEmptyItems) {
            for (int index = 0; index < copy.size(); index++) {
                string(copy.get(index), field + "[" + index + "]");
            }
        }
        return copy;
    }

    static Map<String, Integer> countMap(Map<String, Integer> values, String field) {
        Objects.requireNonNull(values, field + " 不能为空");
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            string(entry.getKey(), field + " key");
            nonNegative(entry.getValue(), field + "." + entry.getKey());
        }
        Map<String, Integer> sorted = new TreeMap<>(values);
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}

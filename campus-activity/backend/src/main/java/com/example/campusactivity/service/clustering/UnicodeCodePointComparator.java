package com.example.campusactivity.service.clustering;

import java.util.Comparator;
import java.util.Objects;

final class UnicodeCodePointComparator implements Comparator<String> {
    static final UnicodeCodePointComparator INSTANCE = new UnicodeCodePointComparator();

    private UnicodeCodePointComparator() {
    }

    @Override
    public int compare(String left, String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");

        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftCodePoint = left.codePointAt(leftIndex);
            int rightCodePoint = right.codePointAt(rightIndex);
            if (leftCodePoint != rightCodePoint) {
                return Integer.compare(leftCodePoint, rightCodePoint);
            }
            leftIndex += Character.charCount(leftCodePoint);
            rightIndex += Character.charCount(rightCodePoint);
        }
        if (leftIndex == left.length()) {
            return rightIndex == right.length() ? 0 : -1;
        }
        return 1;
    }
}

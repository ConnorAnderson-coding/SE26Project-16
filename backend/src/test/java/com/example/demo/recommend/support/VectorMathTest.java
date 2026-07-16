package com.example.demo.recommend.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorMathTest {

    @Test
    void normalizeUnitLength() {
        float[] n = VectorMath.normalize(new float[] {3f, 4f});
        assertEquals(0.6f, n[0], 1e-5);
        assertEquals(0.8f, n[1], 1e-5);
    }

    @Test
    void weightedAveragePrefersHigherWeight() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        float[] avg = VectorMath.weightedAverage(List.of(a, b), List.of(3.0, 1.0));
        assertNotNull(avg);
        assertTrue(avg[0] > avg[1]);
    }

    @Test
    void timeDecayHalfLife() {
        assertEquals(1.0, VectorMath.timeDecayWeight(0, 30), 1e-9);
        assertEquals(0.5, VectorMath.timeDecayWeight(30, 30), 1e-9);
        assertEquals(0.25, VectorMath.timeDecayWeight(60, 30), 1e-9);
    }

    @Test
    void convexCombineMix() {
        float[] interest = VectorMath.normalize(new float[] {1f, 0f});
        float[] history = VectorMath.normalize(new float[] {0f, 1f});
        float[] mixed = VectorMath.convexCombine(interest, history, 0.4);
        assertNotNull(mixed);
        assertTrue(mixed[0] > 0 && mixed[1] > 0);
    }

    @Test
    void minMaxDegenerateReturnsZeroNotOne() {
        assertEquals(0.0, VectorMath.minMaxNormalize(0.0, 0.0, 0.0), 1e-12);
        assertEquals(0.0, VectorMath.minMaxNormalize(3.0, 3.0, 3.0), 1e-12);
        assertEquals(0.5, VectorMath.minMaxNormalize(5.0, 0.0, 10.0), 1e-12);
    }
}

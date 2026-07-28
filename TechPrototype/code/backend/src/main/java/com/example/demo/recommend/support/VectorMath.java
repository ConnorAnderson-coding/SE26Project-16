package com.example.demo.recommend.support;

import java.util.List;

public final class VectorMath {

    private VectorMath() {
    }

    public static float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector;
        }
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += (double) v * v;
        }
        if (sumSq <= 1e-12) {
            return vector.clone();
        }
        double norm = Math.sqrt(sumSq);
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            out[i] = (float) (vector[i] / norm);
        }
        return out;
    }

    /**
     * Weighted average then L2-normalize. Skip null vectors / non-positive weights.
     */
    public static float[] weightedAverage(List<float[]> vectors, List<Double> weights) {
        if (vectors == null || vectors.isEmpty() || weights == null || weights.isEmpty()) {
            return null;
        }
        int dim = -1;
        double[] acc = null;
        double wSum = 0.0;
        for (int i = 0; i < vectors.size(); i++) {
            float[] v = vectors.get(i);
            double w = i < weights.size() ? weights.get(i) : 0.0;
            if (v == null || w <= 0.0) {
                continue;
            }
            if (dim < 0) {
                dim = v.length;
                acc = new double[dim];
            }
            else if (v.length != dim) {
                continue;
            }
            for (int d = 0; d < dim; d++) {
                acc[d] += w * v[d];
            }
            wSum += w;
        }
        if (acc == null || wSum <= 0.0) {
            return null;
        }
        float[] avg = new float[dim];
        for (int d = 0; d < dim; d++) {
            avg[d] = (float) (acc[d] / wSum);
        }
        return normalize(avg);
    }

    public static float[] convexCombine(float[] a, float[] b, double weightA) {
        if (a == null) {
            return b == null ? null : normalize(b.clone());
        }
        if (b == null) {
            return normalize(a.clone());
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException("vector dimensions differ");
        }
        double wa = clamp01(weightA);
        double wb = 1.0 - wa;
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (float) (wa * a[i] + wb * b[i]);
        }
        return normalize(out);
    }

    public static double timeDecayWeight(long daysAgo, double halfLifeDays) {
        if (daysAgo < 0) {
            daysAgo = 0;
        }
        if (halfLifeDays <= 0) {
            return 1.0;
        }
        return Math.pow(0.5, daysAgo / halfLifeDays);
    }

    /**
     * Min-max to [0,1]. When there is no spread ({@code max <= min}), return 0
     * so a constant / all-zero feature does not become a fake full score.
     */
    public static double minMaxNormalize(double value, double min, double max) {
        if (max <= min) {
            return 0.0;
        }
        return (value - min) / (max - min);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

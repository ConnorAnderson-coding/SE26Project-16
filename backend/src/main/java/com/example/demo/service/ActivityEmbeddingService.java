package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ActivityEmbeddingService {

    public float[] embed(String text, int dimensions) {
        float[] vector = new float[dimensions];
        if (!StringUtils.hasText(text)) {
            return vector;
        }

        for (String token : tokenize(text)) {
            int index = Math.floorMod(positiveHash(token), dimensions);
            vector[index] += 1.0f;
        }
        normalize(vector);
        return vector;
    }

    private List<String> tokenize(String text) {
        String[] rawTokens = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .trim()
                .split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String token : rawTokens) {
            if (token.isBlank()) {
                continue;
            }
            tokens.add(token);
            if (containsHan(token)) {
                for (int i = 0; i < token.length(); i++) {
                    tokens.add(token.substring(i, i + 1));
                    if (i + 2 <= token.length()) {
                        tokens.add(token.substring(i, i + 2));
                    }
                    if (i + 3 <= token.length()) {
                        tokens.add(token.substring(i, i + 3));
                    }
                }
            }
        }
        return tokens;
    }

    private boolean containsHan(String token) {
        return token.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private int positiveHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xff) << 24)
                    | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8)
                    | (digest[3] & 0xff);
        } catch (NoSuchAlgorithmException ex) {
            return value.hashCode() & Integer.MAX_VALUE;
        }
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return;
        }
        float length = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / length;
        }
    }
}

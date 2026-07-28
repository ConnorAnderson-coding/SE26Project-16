package com.example.demo.search;

import com.example.demo.entity.Activity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityDocumentMapperTest {

    @Test
    void buildSearchTextIncludesOrganizerFieldsButNotMetrics() {
        Activity activity = new Activity();
        activity.setTitle("校园音乐节");
        activity.setDescription("现场演出");
        activity.setCategory("arts");
        activity.setTags(List.of("音乐", "文艺"));
        activity.setLocation("中心广场");
        activity.setCollege("计算机学院");
        activity.setSignupCount(412);
        activity.setFavoriteCount(198);
        activity.setStatus("ended");

        String text = ActivityDocumentMapper.buildSearchText(activity);

        assertTrue(text.contains("校园音乐节"));
        assertTrue(text.contains("现场演出"));
        assertTrue(text.contains("arts"));
        assertTrue(text.contains("文艺表演"));
        assertTrue(text.contains("音乐"));
        assertTrue(text.contains("文艺"));
        assertTrue(text.contains("中心广场"));
        assertFalse(text.contains("计算机学院"));
        assertFalse(text.contains("412"));
        assertFalse(text.contains("198"));
        assertFalse(text.contains("ended"));
    }
}

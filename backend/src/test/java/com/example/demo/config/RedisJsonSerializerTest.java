package com.example.demo.config;

import com.example.demo.entity.Activity;
import com.example.demo.entity.User;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RedisJsonSerializerTest {

    @Test
    void shouldSerializeAndDeserializeLocalDateTime() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        User organizer = new User();
        organizer.setId("org1");
        organizer.setName("组织者");
        organizer.setRole("teacher");
        organizer.setCollege("计算机学院");
        organizer.setGrade("2024");
        organizer.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        organizer.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        Activity activity = new Activity();
        activity.setId(1L);
        activity.setTitle("讲座");
        activity.setCategory("讲座");
        activity.setDescription("描述");
        activity.setStartTime(LocalDateTime.of(2026, 7, 10, 14, 0));
        activity.setEndTime(LocalDateTime.of(2026, 7, 10, 16, 0));
        activity.setLocation("A101");
        activity.setOrganizer(organizer);
        activity.setCollege("计算机学院");
        activity.setMaxParticipants(50);
        activity.setSignupCount(0);
        activity.setFavoriteCount(0);
        activity.setStatus("published");
        activity.setTags(List.of("AI"));
        activity.setCreatedAt(LocalDateTime.of(2026, 6, 1, 9, 0));
        activity.setUpdatedAt(LocalDateTime.of(2026, 6, 1, 9, 0));

        byte[] bytes = serializer.serialize(activity);
        assertNotNull(bytes);

        Object restored = serializer.deserialize(bytes);
        assertNotNull(restored);
        Activity cached = (Activity) restored;
        assertEquals("讲座", cached.getTitle());
        assertEquals(LocalDateTime.of(2026, 7, 10, 14, 0), cached.getStartTime());
        assertEquals("组织者", cached.getOrganizer().getName());
    }
}

package com.example.demo.config;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import com.example.demo.entity.Activity;
import com.example.demo.entity.User;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

class RedisJsonSerializerTest {

    @Test
    void shouldSerializeAndDeserializeLocalDateTime() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        RedisSerializer<Object> serializer = new ObjectRedisSerializer(objectMapper);

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

    private static final class ObjectRedisSerializer implements RedisSerializer<Object> {
        private final ObjectMapper objectMapper;

        private ObjectRedisSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public byte[] serialize(Object source) throws SerializationException {
            if (source == null) {
                return new byte[0];
            }
            try {
                return objectMapper.writeValueAsBytes(source);
            } catch (JsonProcessingException ex) {
                throw new SerializationException("Failed to serialize object for test", ex);
            }
        }

        @Override
        public Object deserialize(byte[] source) throws SerializationException {
            if (source == null || source.length == 0) {
                return null;
            }
            try {
                return objectMapper.readValue(source, Object.class);
            } catch (IOException ex) {
                throw new SerializationException("Failed to deserialize object for test", ex);
            }
        }
    }
}

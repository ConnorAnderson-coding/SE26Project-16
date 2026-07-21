package com.example.demo.search;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import co.elastic.clients.json.JsonData;

final class ActivityDocumentJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ActivityDocumentJson() {
    }

    static JsonData toJsonData(ActivityDocument document) {
        return JsonData.of(MAPPER.convertValue(document, Map.class));
    }
}

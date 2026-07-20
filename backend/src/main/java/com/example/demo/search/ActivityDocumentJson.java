package com.example.demo.search;

import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

final class ActivityDocumentJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ActivityDocumentJson() {
    }

    @SuppressWarnings("unchecked")
    static JsonData toJsonData(ActivityDocument document) {
        return JsonData.of(MAPPER.convertValue(document, Map.class));
    }
}

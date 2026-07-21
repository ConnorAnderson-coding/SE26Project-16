package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.TriggerClusteringRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriggerClusteringRequestParserTest {
    private final ObjectMapper globalMapper = new ObjectMapper();
    private final TriggerClusteringRequestParser parser =
            new TriggerClusteringRequestParser(globalMapper);

    @Test
    void missingBlankEmptyObjectAndNullUseDefaultTwo() {
        assertThat(parser.parse(null).resolvedClusterCount()).isEqualTo(2);
        assertThat(parse("   ").resolvedClusterCount()).isEqualTo(2);
        assertThat(parse("{}").resolvedClusterCount()).isEqualTo(2);
        assertThat(parse("{\"clusterCount\":null}").resolvedClusterCount()).isEqualTo(2);
    }

    @Test
    void exactIntegerIsAcceptedWithoutChangingIt() {
        TriggerClusteringRequest request = parse("{\"clusterCount\":7}");
        assertThat(request.clusterCount()).isEqualTo(7);
    }

    @Test
    void rejectsUnknownAndIdentityInjectionFields() {
        assertInvalid("{\"unknown\":1}");
        assertInvalid("{\"clusterCount\":2,\"createdBy\":\"attacker\"}");
        assertInvalid("{\"role\":\"admin\"}");
        assertInvalid("{\"algorithm\":\"KMEANS\"}");
    }

    @Test
    void rejectsNonIntegerOverflowMalformedDuplicateAndTrailingJson() {
        assertInvalid("{\"clusterCount\":2.0}");
        assertInvalid("{\"clusterCount\":true}");
        assertInvalid("{\"clusterCount\":2147483648}");
        assertInvalid("{\"clusterCount\":2");
        assertInvalid("{\"clusterCount\":2,\"clusterCount\":3}");
        assertInvalid("{\"clusterCount\":2} {}");
        assertInvalid("[]");
    }

    @Test
    void parserDoesNotMutateGlobalObjectMapper() {
        assertThat(globalMapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS))
                .isFalse();
    }

    private TriggerClusteringRequest parse(String json) {
        return parser.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> parse(json))
                .isInstanceOf(InvalidClusteringRequestException.class)
                .hasMessage("INVALID_CLUSTERING_REQUEST");
    }
}

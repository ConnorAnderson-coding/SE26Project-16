package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.ClusteringMetrics;
import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.CommunitySummary;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.client.clustering.dto.MemberResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClusteringResponseValidatorTest {
    private static final String BMP_PRIVATE_USE = "\uE000"; // U+E000
    private static final String SUPPLEMENTARY_10000 =
            new String(Character.toChars(0x10000)); // U+10000
    private static final String SUPPLEMENTARY_10001 =
            new String(Character.toChars(0x10001)); // U+10001
    private final ClusteringResponseValidator validator = new ClusteringResponseValidator();

    @Test
    void ordersBmpBeforeSupplementaryByUnicodeCodePointUnlikeStringNaturalOrder() {
        assertThat(BMP_PRIVATE_USE.codePointAt(0)).isEqualTo(0xE000);
        assertThat(SUPPLEMENTARY_10000.codePointAt(0)).isEqualTo(0x10000);
        assertThat(BMP_PRIVATE_USE.compareTo(SUPPLEMENTARY_10000)).isPositive();

        assertThat(UnicodeCodePointComparator.INSTANCE.compare(
                BMP_PRIVATE_USE,
                SUPPLEMENTARY_10000
        )).isNegative();
    }

    @Test
    void ordersTwoSupplementaryCharactersByCodePoint() {
        assertThat(UnicodeCodePointComparator.INSTANCE.compare(
                SUPPLEMENTARY_10000,
                SUPPLEMENTARY_10001
        )).isNegative();
        assertThat(UnicodeCodePointComparator.INSTANCE.compare(
                SUPPLEMENTARY_10001,
                SUPPLEMENTARY_10000
        )).isPositive();
    }

    @Test
    void ordersShorterStringFirstWhenCodePointPrefixIsShared() {
        assertThat(UnicodeCodePointComparator.INSTANCE.compare(
                SUPPLEMENTARY_10000,
                SUPPLEMENTARY_10000 + "a"
        )).isNegative();
        assertThat(UnicodeCodePointComparator.INSTANCE.compare(
                SUPPLEMENTARY_10000 + "a",
                SUPPLEMENTARY_10000
        )).isPositive();
    }

    @Test
    void preservesExpectedAsciiAndChineseCodePointOrder() {
        List<String> values = new ArrayList<>(List.of("\u4E2D\u6587", "B", "A"));

        values.sort(UnicodeCodePointComparator.INSTANCE);

        assertThat(values).containsExactly("A", "B", "\u4E2D\u6587");
    }

    @Test
    void acceptsPythonCodePointOrderForEqualFrequencyTopInterests() {
        ClusteringRequest request = unicodeOrderingRequest();
        List<CommunitySummary> communities = unicodeOrderingCommunities(
                List.of(BMP_PRIVATE_USE, SUPPLEMENTARY_10000)
        );
        ClusteringResponse response = responseWith(
                validResponse(), communities, validMembers(), validMetrics());

        assertThat(validator.validate(request, response).communities().get(0).topInterests())
                .containsExactly(BMP_PRIVATE_USE, SUPPLEMENTARY_10000);
    }

    @Test
    void rejectsUtf16NaturalOrderForEqualFrequencyTopInterests() {
        ClusteringRequest request = unicodeOrderingRequest();
        List<CommunitySummary> communities = unicodeOrderingCommunities(
                List.of(SUPPLEMENTARY_10000, BMP_PRIVATE_USE)
        );
        ClusteringResponse response = responseWith(
                validResponse(), communities, validMembers(), validMetrics());

        assertCode(ClusteringResponseValidationCode.INVALID_TOP_INTEREST_ORDER,
                () -> validator.validate(request, response));
    }

    @Test
    void unicodeCodePointTopInterestValidationIsDeterministicAcrossRuns() {
        ClusteringRequest request = unicodeOrderingRequest();
        ClusteringResponse response = responseWith(
                validResponse(),
                unicodeOrderingCommunities(List.of(BMP_PRIVATE_USE, SUPPLEMENTARY_10000)),
                validMembers(),
                validMetrics()
        );

        ValidatedClusteringResult expected = validator.validate(request, response);
        for (int iteration = 0; iteration < 10; iteration++) {
            assertThat(validator.validate(request, response)).isEqualTo(expected);
        }
    }

    @Test
    void acceptsPythonCodePointOrderForMemberUserIds() {
        String bmpUserId = BMP_PRIVATE_USE + "-member";
        String supplementaryUserId = SUPPLEMENTARY_10000 + "-member";
        List<MemberResult> members = List.of(
                member(bmpUserId, 0, 10.0, 20.0, 0.1),
                member(supplementaryUserId, 1, 90.0, 80.0, 0.2)
        );
        ClusteringRequest request = requestForMembers(members);

        ValidatedClusteringResult result = validator.validate(
                request,
                emptyInterestResponse(request, members)
        );

        assertThat(result.members()).extracting(MemberResult::userId)
                .containsExactly(bmpUserId, supplementaryUserId);
    }

    @Test
    void rejectsUtf16NaturalOrderForMemberUserIds() {
        String bmpUserId = BMP_PRIVATE_USE + "-member";
        String supplementaryUserId = SUPPLEMENTARY_10000 + "-member";
        List<MemberResult> pythonOrderedMembers = List.of(
                member(bmpUserId, 0, 10.0, 20.0, 0.1),
                member(supplementaryUserId, 1, 90.0, 80.0, 0.2)
        );
        List<MemberResult> utf16OrderedMembers = List.of(
                pythonOrderedMembers.get(1),
                pythonOrderedMembers.get(0)
        );
        ClusteringRequest request = requestForMembers(pythonOrderedMembers);

        assertCode(
                ClusteringResponseValidationCode.INVALID_MEMBER_ORDER,
                () -> validator.validate(request, emptyInterestResponse(request, utf16OrderedMembers))
        );
    }

    @Test
    void normalizedMemberViewsUseUnicodeCodePointOrder() {
        List<MemberResult> members = prefixedUnicodeMembers();
        ClusteringRequest request = requestForMembers(members);

        ValidatedClusteringResult result = validator.validate(
                request,
                emptyInterestResponse(request, members)
        );

        assertThat(result.members()).extracting(MemberResult::userId)
                .containsExactlyElementsOf(memberUserIds(members));
        assertThat(result.membersByClusterNo().get(0)).extracting(MemberResult::userId)
                .containsExactly("a-" + BMP_PRIVATE_USE, "a-" + SUPPLEMENTARY_10000);
        assertThat(result.membersByClusterNo().get(1)).extracting(MemberResult::userId)
                .containsExactly("b-" + BMP_PRIVATE_USE, "b-" + SUPPLEMENTARY_10000);
    }

    @Test
    void multiClusterMemberGroupsUseCodePointOrderAndClusterNumberOrder() {
        List<MemberResult> members = prefixedUnicodeMembers();
        ClusteringRequest request = requestForMembers(members);

        ValidatedClusteringResult result = validator.validate(
                request,
                emptyInterestResponse(request, members)
        );

        assertThat(result.membersByClusterNo().keySet()).containsExactly(0, 1);
        assertThat(result.membersByClusterNo().get(0)).extracting(MemberResult::userId)
                .containsExactly("a-" + BMP_PRIVATE_USE, "a-" + SUPPLEMENTARY_10000);
        assertThat(result.membersByClusterNo().get(1)).extracting(MemberResult::userId)
                .containsExactly("b-" + BMP_PRIVATE_USE, "b-" + SUPPLEMENTARY_10000);
    }

    @Test
    void acceptsPythonMemberOrderWhenUserIdsShareBmpPrefix() {
        String bmpUserId = "shared-prefix-" + BMP_PRIVATE_USE;
        String supplementaryUserId = "shared-prefix-" + SUPPLEMENTARY_10000;
        List<MemberResult> members = List.of(
                member(bmpUserId, 0, 10.0, 20.0, 0.1),
                member(supplementaryUserId, 1, 90.0, 80.0, 0.2)
        );
        ClusteringRequest request = requestForMembers(members);

        assertThat(validator.validate(request, emptyInterestResponse(request, members)).members())
                .extracting(MemberResult::userId)
                .containsExactly(bmpUserId, supplementaryUserId);
    }

    @Test
    void preservesAsciiAndChineseMemberOrdering() {
        List<MemberResult> members = List.of(
                member("A-user", 0, 10.0, 20.0, 0.1),
                member("B-user", 1, 30.0, 40.0, 0.2),
                member("\u4E2D-user", 0, 50.0, 60.0, 0.3),
                member("\u7F16-user", 1, 70.0, 80.0, 0.4)
        );
        ClusteringRequest request = requestForMembers(members);

        assertThat(validator.validate(request, emptyInterestResponse(request, members)).members())
                .extracting(MemberResult::userId)
                .containsExactly("A-user", "B-user", "\u4E2D-user", "\u7F16-user");
    }

    @Test
    void unicodeMemberValidationIsDeterministicAcrossRuns() {
        List<MemberResult> members = prefixedUnicodeMembers();
        ClusteringRequest request = requestForMembers(members);
        ClusteringResponse response = emptyInterestResponse(request, members);

        ValidatedClusteringResult expected = validator.validate(request, response);
        for (int iteration = 0; iteration < 10; iteration++) {
            assertThat(validator.validate(request, response)).isEqualTo(expected);
        }
    }

    @Test
    void acceptsValidResultAndBuildsStableNormalizedViews() {
        ClusteringRequest request = validRequest();
        ClusteringResponse response = validResponse();

        ValidatedClusteringResult result = validator.validate(request, response);

        assertThat(result.runId()).isEqualTo(request.runId());
        assertThat(result.version()).isEqualTo(request.version());
        assertThat(result.algorithm()).isEqualTo("KMEANS");
        assertThat(result.clusterCount()).isEqualTo(2);
        assertThat(result.sampleCount()).isEqualTo(4);
        assertThat(result.communities()).extracting(CommunitySummary::clusterNo)
                .containsExactly(0, 1);
        assertThat(result.members()).extracting(MemberResult::userId)
                .containsExactly("u1", "u2", "u3", "u4");
        assertThat(result.membersByClusterNo().keySet()).containsExactly(0, 1);
        assertThat(result.membersByClusterNo().get(0)).extracting(MemberResult::userId)
                .containsExactly("u1", "u2");
        assertThat(result.membersByClusterNo().get(1)).extracting(MemberResult::userId)
                .containsExactly("u3", "u4");
    }

    @Test
    void inputSampleOrderDoesNotAffectValidationResult() {
        ClusteringRequest original = validRequest();
        List<FeatureSample> reversed = new ArrayList<>(original.samples());
        Collections.reverse(reversed);
        ClusteringRequest reordered = requestWithSamples(reversed);

        assertThat(validator.validate(reordered, validResponse()))
                .isEqualTo(validator.validate(original, validResponse()));
    }

    @Test
    void unorderedCommunitiesAreNormalizedAndDoNotAffectEquality() {
        ClusteringResponse ordered = validResponse();
        List<CommunitySummary> reversed = new ArrayList<>(ordered.communities());
        Collections.reverse(reversed);
        ClusteringResponse unordered = responseWith(ordered, reversed, ordered.members(), ordered.metrics());

        ValidatedClusteringResult expected = validator.validate(validRequest(), ordered);
        ValidatedClusteringResult actual = validator.validate(validRequest(), unordered);

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.communities()).extracting(CommunitySummary::clusterNo)
                .containsExactly(0, 1);
    }

    @Test
    void repeatedValidationProducesEqualResults() {
        assertThat(validator.validate(validRequest(), validResponse()))
                .isEqualTo(validator.validate(validRequest(), validResponse()));
    }

    @Test
    void normalizedCollectionsAreUnmodifiable() {
        ValidatedClusteringResult result = validator.validate(validRequest(), validResponse());

        assertThatThrownBy(() -> result.communities().add(result.communities().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.members().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.membersByClusterNo().put(2, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.membersByClusterNo().get(0).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullRequest() {
        assertCode(ClusteringResponseValidationCode.INVALID_REQUEST,
                () -> validator.validate(null, validResponse()));
    }

    @Test
    void rejectsNullResponse() {
        assertCode(ClusteringResponseValidationCode.INVALID_RESPONSE,
                () -> validator.validate(validRequest(), null));
    }

    @Test
    void defensivelyRejectsDuplicateInputUsers() {
        FeatureSample duplicate = sample("same-user", List.of());
        ClusteringRequest request = mock(ClusteringRequest.class);
        when(request.clusterCount()).thenReturn(2);
        when(request.samples()).thenReturn(List.of(duplicate, duplicate));

        assertCode(ClusteringResponseValidationCode.INPUT_USER_DUPLICATE,
                () -> validator.validate(request, validResponse()));
    }

    @ParameterizedTest
    @MethodSource("invalidRequestBounds")
    void defensivelyRejectsInvalidRequestBounds(int clusterCount, List<FeatureSample> samples) {
        ClusteringRequest request = mock(ClusteringRequest.class);
        when(request.clusterCount()).thenReturn(clusterCount);
        when(request.samples()).thenReturn(samples);

        assertCode(ClusteringResponseValidationCode.INVALID_REQUEST,
                () -> validator.validate(request, validResponse()));
    }

    @Test
    void rejectsRunIdMismatchWithoutNormalizingStrings() {
        ClusteringResponse response = copyResponse(validResponse());
        when(response.runId()).thenReturn(" run-safe ");
        assertCode(ClusteringResponseValidationCode.RUN_ID_MISMATCH,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsVersionMismatch() {
        ClusteringResponse response = copyResponse(validResponse());
        when(response.version()).thenReturn("different-version");
        assertCode(ClusteringResponseValidationCode.VERSION_MISMATCH,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void defensivelyRejectsAlgorithmMismatch() {
        ClusteringResponse response = copyResponse(validResponse());
        when(response.algorithm()).thenReturn("OTHER");
        assertCode(ClusteringResponseValidationCode.ALGORITHM_MISMATCH,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsClusterCountMismatch() {
        ClusteringResponse response = copyResponse(validResponse());
        when(response.clusterCount()).thenReturn(3);
        assertCode(ClusteringResponseValidationCode.CLUSTER_COUNT_MISMATCH,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsSampleCountMismatch() {
        ClusteringResponse response = copyResponse(validResponse());
        when(response.sampleCount()).thenReturn(3);
        assertCode(ClusteringResponseValidationCode.SAMPLE_COUNT_MISMATCH,
                () -> validator.validate(validRequest(), response));
    }

    @ParameterizedTest
    @MethodSource("invalidCommunityCounts")
    void rejectsCommunityCountMismatch(List<CommunitySummary> communities) {
        ClusteringResponse base = validResponse();
        ClusteringResponse response = responseWith(base, communities, base.members(), base.metrics());
        assertCode(ClusteringResponseValidationCode.COMMUNITY_COUNT_MISMATCH,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsDuplicateClusterNumber() {
        CommunitySummary first = community(0, 2, List.of("AI", "摄影", "编程"));
        CommunitySummary second = community(0, 2, List.of("AI", "摄影", "编程"));
        ClusteringResponse response = responseWith(
                validResponse(), List.of(first, second), validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.DUPLICATE_CLUSTER_NUMBER,
                () -> validator.validate(validRequest(), response));
    }

    @ParameterizedTest
    @MethodSource("invalidClusterNumbers")
    void rejectsInvalidClusterNumber(Integer clusterNo) {
        CommunitySummary invalid = mockCommunity(clusterNo, 2, List.of("AI"));
        ClusteringResponse response = responseWith(
                validResponse(), List.of(invalid, community(1, 2, List.of("羽毛球"))),
                validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.INVALID_CLUSTER_NUMBER,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void incompleteClusterSetIsRejectedByMoreSpecificDuplicateCheck() {
        ClusteringResponse response = responseWith(
                validResponse(),
                List.of(community(0, 2, List.of("AI")), community(0, 2, List.of("AI"))),
                validMembers(),
                validMetrics());
        assertCode(ClusteringResponseValidationCode.DUPLICATE_CLUSTER_NUMBER,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void defensivelyRejectsNonPositiveCommunityMemberCount() {
        CommunitySummary invalid = mockCommunity(0, 0, List.of("AI"));
        ClusteringResponse response = responseWith(validResponse(),
                List.of(invalid, community(1, 2, List.of("羽毛球"))), validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.INVALID_COMMUNITY_VALUE,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void defensivelyRejectsNullTopInterests() {
        CommunitySummary invalid = mockCommunity(0, 2, null);
        ClusteringResponse response = responseWith(validResponse(),
                List.of(invalid, community(1, 2, List.of("羽毛球"))), validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.INVALID_TOP_INTEREST,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsBlankTopInterest() {
        ClusteringResponse response = responseWith(validResponse(),
                List.of(community(0, 2, List.of("AI", "  ")), community(1, 2, List.of("羽毛球"))),
                validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.INVALID_TOP_INTEREST,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsDuplicateTopInterest() {
        ClusteringResponse response = responseWith(validResponse(),
                List.of(community(0, 2, List.of("AI", "AI")), community(1, 2, List.of("羽毛球"))),
                validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.DUPLICATE_TOP_INTEREST,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void defensivelyRejectsMoreThanThreeTopInterests() {
        CommunitySummary invalid = mockCommunity(0, 2, List.of("a", "b", "c", "d"));
        ClusteringResponse response = responseWith(validResponse(),
                List.of(invalid, community(1, 2, List.of("羽毛球"))), validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.INVALID_TOP_INTEREST,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void defensivelyRejectsDuplicateResponseUsers() {
        List<MemberResult> members = List.of(
                member("u1", 0, 10.0, 20.0, 0.1),
                member("u1", 0, 20.0, 30.0, 0.2),
                member("u3", 1, 70.0, 80.0, 0.3),
                member("u4", 1, 90.0, 100.0, 0.4)
        );
        ClusteringResponse response = copyResponse(validResponse());
        when(response.members()).thenReturn(members);
        assertCode(ClusteringResponseValidationCode.RESPONSE_USER_DUPLICATE,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsMissingInputUser() {
        List<MemberResult> members = validMembers().subList(0, 3);
        ClusteringResponse response = responseWith(
                validResponse(), validCommunities(), members, validMetrics());
        assertCode(ClusteringResponseValidationCode.RESPONSE_USER_MISSING,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsUnexpectedResponseUser() {
        List<MemberResult> members = new ArrayList<>(validMembers());
        members.add(member("unexpected-user", 1, 50.0, 50.0, 0.0));
        ClusteringResponse response = responseWith(
                validResponse(), validCommunities(), members, validMetrics());
        assertCode(ClusteringResponseValidationCode.RESPONSE_USER_UNEXPECTED,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsMemberReferencingUnknownCluster() {
        List<MemberResult> members = new ArrayList<>(validMembers());
        members.set(3, member("u4", 2, 90.0, 100.0, 0.4));
        ClusteringResponse response = responseWith(
                validResponse(), validCommunities(), members, validMetrics());
        assertCode(ClusteringResponseValidationCode.MEMBER_CLUSTER_UNKNOWN,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsMembersNotOrderedByUserId() {
        List<MemberResult> members = new ArrayList<>(validMembers());
        Collections.swap(members, 0, 1);
        ClusteringResponse response = responseWith(
                validResponse(), validCommunities(), members, validMetrics());
        assertCode(ClusteringResponseValidationCode.INVALID_MEMBER_ORDER,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsSummaryMemberTotalMismatch() {
        List<CommunitySummary> communities = List.of(
                community(0, 3, List.of("AI", "摄影", "编程")),
                community(1, 2, List.of("羽毛球"))
        );
        ClusteringResponse response = responseWith(
                validResponse(), communities, validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.TOTAL_MEMBER_COUNT_MISMATCH,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsPerCommunityMemberCountMismatchWhenTotalMatches() {
        List<CommunitySummary> communities = List.of(
                community(0, 1, List.of("AI", "摄影", "编程")),
                community(1, 3, List.of("羽毛球"))
        );
        ClusteringResponse response = responseWith(
                validResponse(), communities, validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.COMMUNITY_MEMBER_COUNT_MISMATCH,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsEmptyCommunity() {
        List<MemberResult> members = List.of(
                member("u1", 0, 10.0, 20.0, 0.1),
                member("u2", 0, 20.0, 30.0, 0.2),
                member("u3", 0, 70.0, 80.0, 0.3),
                member("u4", 0, 90.0, 100.0, 0.4)
        );
        List<CommunitySummary> communities = List.of(
                community(0, 3, List.of("AI")),
                community(1, 1, List.of("羽毛球"))
        );
        ClusteringResponse response = responseWith(validResponse(), communities, members, validMetrics());
        assertCode(ClusteringResponseValidationCode.COMMUNITY_MEMBER_COUNT_MISMATCH,
                () -> validator.validate(validRequest(), response));
    }

    @ParameterizedTest
    @MethodSource("invalidMembers")
    void defensivelyRejectsInvalidMemberValues(MemberResult invalidMember) {
        List<MemberResult> members = new ArrayList<>(validMembers());
        members.set(0, invalidMember);
        ClusteringResponse response = responseWith(
                validResponse(), validCommunities(), members, validMetrics());
        assertCode(ClusteringResponseValidationCode.INVALID_MEMBER_VALUE,
                () -> validator.validate(validRequest(), response));
    }

    @ParameterizedTest
    @MethodSource("invalidMetrics")
    void defensivelyRejectsInvalidMetrics(ClusteringMetrics metrics) {
        ClusteringResponse response = responseWith(
                validResponse(), validCommunities(), validMembers(), metrics);
        assertCode(ClusteringResponseValidationCode.INVALID_METRICS,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void rejectsTopInterestNotPresentInCluster() {
        List<CommunitySummary> communities = List.of(
                community(0, 2, List.of("羽毛球")),
                community(1, 2, List.of("羽毛球"))
        );
        ClusteringResponse response = responseWith(
                validResponse(), communities, validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.TOP_INTEREST_NOT_IN_CLUSTER,
                () -> validator.validate(validRequest(), response));
    }

    @ParameterizedTest
    @MethodSource("incorrectRankedInterests")
    void rejectsIncorrectTopInterestRanking(List<String> interests) {
        List<CommunitySummary> communities = List.of(
                community(0, 2, interests),
                community(1, 2, List.of("羽毛球"))
        );
        ClusteringResponse response = responseWith(
                validResponse(), communities, validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.INVALID_TOP_INTEREST_ORDER,
                () -> validator.validate(validRequest(), response));
    }

    @Test
    void countsEachInterestAtMostOncePerUserLikePythonSet() {
        ClusteringRequest request = requestWithSamples(List.of(
                sample("u1", List.of("AI", "AI", "摄影")),
                sample("u2", List.of("摄影")),
                sample("u3", List.of()),
                sample("u4", List.of())
        ));
        List<CommunitySummary> communities = List.of(
                community(0, 2, List.of("摄影", "AI")),
                community(1, 2, List.of())
        );
        ClusteringResponse response = responseWith(
                validResponse(), communities, validMembers(), validMetrics());

        assertThat(validator.validate(request, response).communities().get(0).topInterests())
                .containsExactly("摄影", "AI");
    }

    @Test
    void allowsEmptyTopInterestsWhenClusterMembersHaveNoInterests() {
        ClusteringRequest request = requestWithSamples(List.of(
                sample("u1", List.of()), sample("u2", List.of()),
                sample("u3", List.of()), sample("u4", List.of())
        ));
        List<CommunitySummary> communities = List.of(
                community(0, 2, List.of()), community(1, 2, List.of())
        );
        ClusteringResponse response = responseWith(
                validResponse(), communities, validMembers(), validMetrics());

        assertThat(validator.validate(request, response).communities())
                .allMatch(community -> community.topInterests().isEmpty());
    }

    @Test
    void rejectsEmptyTopInterestsWhenRankedInterestsExist() {
        List<CommunitySummary> communities = List.of(
                community(0, 2, List.of()), community(1, 2, List.of("羽毛球"))
        );
        ClusteringResponse response = responseWith(
                validResponse(), communities, validMembers(), validMetrics());
        assertCode(ClusteringResponseValidationCode.INVALID_TOP_INTEREST_ORDER,
                () -> validator.validate(validRequest(), response));
    }

    @ParameterizedTest
    @EnumSource(ClusteringResponseValidationCode.class)
    void everyValidationCodeProducesOnlyItsFixedSafeMessage(ClusteringResponseValidationCode code) {
        ClusteringResponseValidationException exception = new ClusteringResponseValidationException(code);

        assertThat(exception.getCode()).isSameAs(code);
        assertThat(exception.getMessage()).isEqualTo(code.message());
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getMessage()).doesNotContain(
                "sensitive-run", "sensitive-version", "sensitive-user", "sensitive-interest", "99.99"
        );
    }

    @Test
    void validationExceptionDoesNotExposeDynamicValuesAnywhereInStackText() {
        ClusteringRequest request = validRequest();
        ClusteringResponse response = copyResponse(validResponse());
        when(response.runId()).thenReturn("sensitive-run-value");
        when(response.version()).thenReturn("sensitive-version-value");

        Throwable exception = catchThrowable(() -> validator.validate(request, response));
        StringWriter stack = new StringWriter();
        exception.printStackTrace(new PrintWriter(stack));

        assertThat(exception).isInstanceOf(ClusteringResponseValidationException.class);
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getMessage()).doesNotContain(
                "sensitive-run-value", "sensitive-version-value", "u1", "AI", "10.0"
        );
        assertThat(exception.toString()).doesNotContain(
                "sensitive-run-value", "sensitive-version-value", "u1", "AI", "10.0"
        );
        assertThat(stack.toString()).doesNotContain(
                "sensitive-run-value", "sensitive-version-value", "u1", "AI", "10.0"
        );
    }

    @Test
    void validationExceptionStoresOnlyFixedValidationCode() {
        List<Field> instanceFields = Stream.of(ClusteringResponseValidationException.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .toList();

        assertThat(instanceFields).singleElement()
                .extracting(Field::getType)
                .isEqualTo(ClusteringResponseValidationCode.class);
    }

    private static Stream<Arguments> invalidRequestBounds() {
        return Stream.of(
                Arguments.of(1, List.of(sample("u1", List.of()), sample("u2", List.of()))),
                Arguments.of(3, List.of(sample("u1", List.of()), sample("u2", List.of())))
        );
    }

    private static Stream<Arguments> invalidCommunityCounts() {
        return Stream.of(
                Arguments.of(List.of(community(0, 4, List.of("AI")))),
                Arguments.of(List.of(
                        community(0, 2, List.of("AI", "摄影", "编程")),
                        community(1, 2, List.of("羽毛球")),
                        community(2, 1, List.of())
                ))
        );
    }

    private static Stream<Arguments> invalidClusterNumbers() {
        return Stream.of(Arguments.of(-1), Arguments.of(2), Arguments.of((Integer) null));
    }

    private static Stream<Arguments> invalidMembers() {
        return Stream.of(
                Arguments.of(mockMember("u1", 0, Double.NaN, 20.0, 0.1)),
                Arguments.of(mockMember("u1", 0, -0.1, 20.0, 0.1)),
                Arguments.of(mockMember("u1", 0, 100.1, 20.0, 0.1)),
                Arguments.of(mockMember("u1", 0, 10.0, Double.POSITIVE_INFINITY, 0.1)),
                Arguments.of(mockMember("u1", 0, 10.0, -0.1, 0.1)),
                Arguments.of(mockMember("u1", 0, 10.0, 100.1, 0.1)),
                Arguments.of(mockMember("u1", 0, 10.0, 20.0, Double.NaN)),
                Arguments.of(mockMember("u1", 0, 10.0, 20.0, -0.1)),
                Arguments.of(mockMember("u1", 0, 10.0, 20.0, Double.POSITIVE_INFINITY))
        );
    }

    private static Stream<Arguments> invalidMetrics() {
        return Stream.of(
                Arguments.of(mockMetrics(null, List.of(0.5, 0.5))),
                Arguments.of(mockMetrics(-0.1, List.of(0.5, 0.5))),
                Arguments.of(mockMetrics(Double.NaN, List.of(0.5, 0.5))),
                Arguments.of(mockMetrics(1.0, null)),
                Arguments.of(mockMetrics(1.0, List.of(1.0))),
                Arguments.of(mockMetrics(1.0, List.of(1.0, 0.0, 0.0))),
                Arguments.of(mockMetrics(1.0, List.of(Double.NaN, 0.0))),
                Arguments.of(mockMetrics(1.0, List.of(-0.1, 0.5))),
                Arguments.of(mockMetrics(1.0, List.of(1.1, 0.0)))
        );
    }

    private static Stream<Arguments> incorrectRankedInterests() {
        return Stream.of(
                Arguments.of(List.of("摄影", "AI", "编程")),
                Arguments.of(List.of("AI", "编程", "摄影")),
                Arguments.of(List.of("AI", "摄影"))
        );
    }

    private static ClusteringRequest validRequest() {
        return requestWithSamples(List.of(
                sample("u1", List.of("AI", "摄影")),
                sample("u2", List.of("AI", "编程")),
                sample("u3", List.of("羽毛球")),
                sample("u4", List.of())
        ));
    }

    private static ClusteringRequest unicodeOrderingRequest() {
        return requestWithSamples(List.of(
                sample("u1", List.of(BMP_PRIVATE_USE, SUPPLEMENTARY_10000)),
                sample("u2", List.of()),
                sample("u3", List.of()),
                sample("u4", List.of())
        ));
    }

    private static List<CommunitySummary> unicodeOrderingCommunities(List<String> clusterZeroInterests) {
        return List.of(
                community(0, 2, clusterZeroInterests),
                community(1, 2, List.of())
        );
    }

    private static List<MemberResult> prefixedUnicodeMembers() {
        return List.of(
                member("a-" + BMP_PRIVATE_USE, 0, 10.0, 20.0, 0.1),
                member("a-" + SUPPLEMENTARY_10000, 0, 30.0, 40.0, 0.2),
                member("b-" + BMP_PRIVATE_USE, 1, 50.0, 60.0, 0.3),
                member("b-" + SUPPLEMENTARY_10000, 1, 70.0, 80.0, 0.4)
        );
    }

    private static List<String> memberUserIds(List<MemberResult> members) {
        return members.stream().map(MemberResult::userId).toList();
    }

    private static ClusteringRequest requestForMembers(List<MemberResult> members) {
        return requestWithSamples(members.stream()
                .map(member -> sample(member.userId(), List.of()))
                .toList());
    }

    private static ClusteringResponse emptyInterestResponse(
            ClusteringRequest request,
            List<MemberResult> members
    ) {
        List<CommunitySummary> communities = new ArrayList<>();
        for (int clusterNo = 0; clusterNo < request.clusterCount(); clusterNo++) {
            int currentClusterNo = clusterNo;
            int memberCount = (int) members.stream()
                    .filter(member -> member.clusterNo() == currentClusterNo)
                    .count();
            communities.add(community(clusterNo, memberCount, List.of()));
        }
        return new ClusteringResponse(
                request.runId(),
                request.version(),
                request.algorithm(),
                request.clusterCount(),
                members.size(),
                validMetrics(),
                communities,
                members
        );
    }

    private static ClusteringRequest requestWithSamples(List<FeatureSample> samples) {
        return new ClusteringRequest(
                "run-safe", "version-safe", "KMEANS", 2, 42,
                "community-features-v1", samples
        );
    }

    private static ClusteringResponse validResponse() {
        return new ClusteringResponse(
                "run-safe", "version-safe", "KMEANS", 2, 4,
                validMetrics(), validCommunities(), validMembers()
        );
    }

    private static List<CommunitySummary> validCommunities() {
        return List.of(
                community(0, 2, List.of("AI", "摄影", "编程")),
                community(1, 2, List.of("羽毛球"))
        );
    }

    private static List<MemberResult> validMembers() {
        return List.of(
                member("u1", 0, 10.0, 20.0, 0.1),
                member("u2", 0, 20.0, 30.0, 0.2),
                member("u3", 1, 70.0, 80.0, 0.3),
                member("u4", 1, 90.0, 100.0, 0.4)
        );
    }

    private static ClusteringMetrics validMetrics() {
        return new ClusteringMetrics(1.5, List.of(0.6, 0.3));
    }

    private static FeatureSample sample(String userId, List<String> interests) {
        return new FeatureSample(
                userId, interests, null, null, List.of(),
                0, 0, 0, 0, 0, null, Map.of()
        );
    }

    private static CommunitySummary community(int clusterNo, int memberCount, List<String> interests) {
        return new CommunitySummary(clusterNo, memberCount, interests);
    }

    private static MemberResult member(
            String userId,
            int clusterNo,
            double x,
            double y,
            double distance
    ) {
        return new MemberResult(userId, clusterNo, x, y, distance);
    }

    private static ClusteringResponse responseWith(
            ClusteringResponse base,
            List<CommunitySummary> communities,
            List<MemberResult> members,
            ClusteringMetrics metrics
    ) {
        return new ClusteringResponse(
                base.runId(), base.version(), base.algorithm(), base.clusterCount(), base.sampleCount(),
                metrics, communities, members
        );
    }

    private static ClusteringResponse copyResponse(ClusteringResponse source) {
        ClusteringResponse response = mock(ClusteringResponse.class);
        when(response.runId()).thenReturn(source.runId());
        when(response.version()).thenReturn(source.version());
        when(response.algorithm()).thenReturn(source.algorithm());
        when(response.clusterCount()).thenReturn(source.clusterCount());
        when(response.sampleCount()).thenReturn(source.sampleCount());
        when(response.metrics()).thenReturn(source.metrics());
        when(response.communities()).thenReturn(source.communities());
        when(response.members()).thenReturn(source.members());
        return response;
    }

    private static CommunitySummary mockCommunity(
            Integer clusterNo,
            Integer memberCount,
            List<String> interests
    ) {
        CommunitySummary community = mock(CommunitySummary.class);
        when(community.clusterNo()).thenReturn(clusterNo);
        when(community.memberCount()).thenReturn(memberCount);
        when(community.topInterests()).thenReturn(interests);
        return community;
    }

    private static MemberResult mockMember(
            String userId,
            Integer clusterNo,
            Double x,
            Double y,
            Double distance
    ) {
        MemberResult member = mock(MemberResult.class);
        when(member.userId()).thenReturn(userId);
        when(member.clusterNo()).thenReturn(clusterNo);
        when(member.coordinateX()).thenReturn(x);
        when(member.coordinateY()).thenReturn(y);
        when(member.distanceToCenter()).thenReturn(distance);
        return member;
    }

    private static ClusteringMetrics mockMetrics(Double inertia, List<Double> ratios) {
        ClusteringMetrics metrics = mock(ClusteringMetrics.class);
        when(metrics.inertia()).thenReturn(inertia);
        when(metrics.pcaExplainedVarianceRatio()).thenReturn(ratios);
        return metrics;
    }

    private static void assertCode(
            ClusteringResponseValidationCode expected,
            Runnable invocation
    ) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(
                        ClusteringResponseValidationException.class,
                        exception -> {
                            assertThat(exception.getCode()).isSameAs(expected);
                            assertThat(exception.getMessage()).isEqualTo(expected.message());
                            assertThat(exception.getCause()).isNull();
                        }
                );
    }
}

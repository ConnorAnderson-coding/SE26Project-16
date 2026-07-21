package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.CommunityMembersPageResponse;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AdminCommunityMemberControllerTest {
    @Test
    void forwardsOnlyPathAndPaginationValuesToQueryService() {
        CommunityClusteringQueryService service =
                mock(CommunityClusteringQueryService.class);
        AdminCommunityMemberController controller =
                new AdminCommunityMemberController(service);
        CommunityMembersPageResponse response = new CommunityMembersPageResponse(
                null, List.of(), 3, 9, 0, 0
        );
        when(service.findCommunityMembers(" community ", "3", "9"))
                .thenReturn(response);

        assertThat(controller.findMembers(" community ", "3", "9"))
                .isSameAs(response);
        verify(service).findCommunityMembers(" community ", "3", "9");
        verifyNoMoreInteractions(service);
    }

    @Test
    void hasOnlyQueryServiceDependencyAndNoTransaction() {
        List<Field> fields = Arrays.stream(
                        AdminCommunityMemberController.class.getDeclaredFields()
                )
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        assertThat(fields).extracting(Field::getType)
                .containsExactly(CommunityClusteringQueryService.class);
        assertThat(AdminCommunityMemberController.class
                .isAnnotationPresent(Transactional.class)).isFalse();
    }
}

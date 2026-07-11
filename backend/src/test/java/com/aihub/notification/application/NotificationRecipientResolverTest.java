/*
 * 功能: NotificationRecipientResolver 单元测试。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aihub.asset.discussion.domain.DiscussionSubscriptionRepository;
import com.aihub.authorization.domain.Permissions;
import com.aihub.organization.domain.TeamMember;
import com.aihub.organization.domain.TeamRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationRecipientResolverTest {

    private JdbcTemplate jdbcTemplate;
    private TeamRepository teamRepository;
    private DiscussionSubscriptionRepository subscriptionRepository;
    private NotificationRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        teamRepository = mock(TeamRepository.class);
        subscriptionRepository = mock(DiscussionSubscriptionRepository.class);
        resolver = new NotificationRecipientResolver(jdbcTemplate, teamRepository, subscriptionRepository);
    }

    @Test
    void resolveSystemObserversReturnsPlatformBindings() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(Permissions.SYSTEM_OBSERVE)))
                .thenReturn(List.of("usr_obs"));

        assertThat(resolver.resolveSystemObservers()).containsExactly("usr_obs");
    }

    @Test
    void resolveAssetOwnersIncludesTeamMembersAndLegacyOwners() {
        when(jdbcTemplate.queryForList(anyString(), eq("ast_01")))
                .thenReturn(List.of(Map.of(
                        "organization_id", "org_01",
                        "project_id", "prj_01",
                        "owner_team_id", "team_01",
                        "owners_json", "[\"prn_legacy\"]")));
        when(teamRepository.findMembersByTeamId("team_01")).thenReturn(List.of(
                new TeamMember("team_01", "usr_member", "MEMBER", "usr_admin", Instant.now())));

        Set<String> owners = resolver.resolveAssetOwners("ast_01");

        assertThat(owners).containsExactlyInAnyOrder("usr_member", "prn_legacy");
    }

    @Test
    void resolveSubscribersDelegatesToRepository() {
        when(subscriptionRepository.findSubscribers("ast_01")).thenReturn(Set.of("usr_sub"));

        assertThat(resolver.resolveSubscribers("ast_01")).containsExactly("usr_sub");
    }
}

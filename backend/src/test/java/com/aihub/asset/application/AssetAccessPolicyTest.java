/*
 * 功能: 资产访问策略单元测试——Team 成员与 ACL 授权路径。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.AccessScope;
import com.aihub.organization.domain.TeamMember;
import com.aihub.organization.domain.TeamRepository;
import com.aihub.shared.identity.PrincipalContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetAccessPolicyTest {

    @Mock private AuthorizationService authorizationService;
    @Mock private TeamRepository teamRepository;

    private AssetAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AssetAccessPolicy(authorizationService, teamRepository);
        when(authorizationService.isPermitted(any())).thenReturn(true);
        when(authorizationService.computeAccessScope(any(), any()))
                .thenReturn(new AccessScope("usr_01", false, Set.of(), Set.of(), Set.of()));
    }

    @Test
    void privateAssetAccessibleToOwnerTeamMember() {
        Asset asset = Asset.create("ast_01", AssetType.MODEL, null, null, "nlp", "demo",
                "Demo", null, Visibility.PRIVATE, List.of(), List.of(), null, "Apache-2.0",
                "team_01", new ModelProfile("pytorch", "text-generation", null), null, "usr_01");
        when(teamRepository.findMember("team_01", "usr_member"))
                .thenReturn(Optional.of(new TeamMember("team_01", "usr_member", "MEMBER", "usr_01", Instant.now())));

        assertThat(policy.canAccess(asset, "usr_member")).isTrue();
    }

    @Test
    void privateAssetDeniedToNonMemberWithoutAcl() {
        Asset asset = Asset.create("ast_01", AssetType.MODEL, null, null, "nlp", "demo",
                "Demo", null, Visibility.PRIVATE, List.of(), List.of(), null, "Apache-2.0",
                "team_01", new ModelProfile("pytorch", "text-generation", null), null, "usr_01");
        when(teamRepository.findMember("team_01", "usr_other")).thenReturn(Optional.empty());
        when(authorizationService.isPermitted(eq("asset:manage"))).thenReturn(false);
        when(authorizationService.isResourcePermitted(
                any(PrincipalContext.class), eq("asset:read"), eq("ASSET"), eq("ast_01")))
                .thenReturn(false);

        assertThat(policy.canAccess(asset, "usr_other")).isFalse();
    }
}

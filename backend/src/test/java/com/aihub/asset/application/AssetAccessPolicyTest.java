/*
 * 功能: 资产可见性判定矩阵单元测试——覆盖 PUBLIC/INTERNAL/PRIVATE 的可访问边界与默认拒绝。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.Visibility;
import com.aihub.authorization.domain.AccessScope;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link AssetAccessPolicy} 可见性判定矩阵测试。
 *
 * <p>对应 TASK-P0BR-017 第 4.1 节判定矩阵与 {@code AC-P0B-AUTH-003}：
 * PUBLIC 对所有已认证主体可见；INTERNAL 仅组织/项目作用域内可见；PRIVATE 仅 ACL 或 Owner Team 可见。
 * 该判定必须与 {@code AssetSearchDao.appendAccessScopeFilter} 的 SQL 条件语义一致。
 */
class AssetAccessPolicyTest {

    private final AssetAccessPolicy policy = new AssetAccessPolicy();

    private static Asset asset(Visibility visibility, String organizationId,
                               String projectId, String ownerTeamId) {
        return Asset.create("ast_1", AssetType.MODEL, organizationId, projectId, "nlp", "sample-asset",
                "显示名", "描述", visibility, List.of("team-nlp"), List.of(),
                null, "Apache-2.0", ownerTeamId,
                new com.aihub.asset.domain.ModelProfile("pytorch", "text-generation", null),
                null, "usr_creator");
    }

    private static AccessScope scope(boolean platformAdmin, Set<String> orgIds,
                                     Set<String> projectIds, Set<String> teamIds,
                                     Set<String> aclIds) {
        return new AccessScope("usr_1", platformAdmin, orgIds, projectIds, teamIds, aclIds);
    }

    @Test
    void publicAssetIsVisibleToAnyAuthenticatedPrincipalWithoutScope() {
        Asset asset = asset(Visibility.PUBLIC, "org_a", "prj_a", "team_a");

        assertThat(policy.canAccess(asset, scope(false, Set.of(), Set.of(), Set.of(), Set.of())))
                .isTrue();
    }

    @Test
    void internalAssetIsVisibleWithinOrganizationScope() {
        Asset asset = asset(Visibility.INTERNAL, "org_a", "prj_a", "team_a");

        assertThat(policy.canAccess(asset, scope(false, Set.of("org_a"), Set.of(), Set.of(), Set.of())))
                .isTrue();
    }

    @Test
    void internalAssetIsNotVisibleToPrincipalOutsideOrganizationAndProject() {
        Asset asset = asset(Visibility.INTERNAL, "org_b", "prj_b", "team_b");

        assertThat(policy.canAccess(asset, scope(false, Set.of("org_a"), Set.of("prj_a"),
                Set.of(), Set.of()))).isFalse();
    }

    @Test
    void privateAssetIsNotVisibleToSameOrganizationMemberWithoutAclOrOwnership() {
        Asset asset = asset(Visibility.PRIVATE, "org_a", "prj_a", "team_a");

        // 关键回归：同组织/同项目但非 Owner 且无 ACL 的主体不得读取 PRIVATE 资产。
        assertThat(policy.canAccess(asset, scope(false, Set.of("org_a"), Set.of("prj_a"),
                Set.of("team_other"), Set.of()))).isFalse();
    }

    @Test
    void privateAssetIsVisibleToOwnerTeamMember() {
        Asset asset = asset(Visibility.PRIVATE, "org_a", "prj_a", "team_a");

        assertThat(policy.canAccess(asset, scope(false, Set.of(), Set.of(),
                Set.of("team_a"), Set.of()))).isTrue();
    }

    @Test
    void privateAssetIsVisibleThroughExplicitAcl() {
        Asset asset = asset(Visibility.PRIVATE, "org_a", "prj_a", "team_a");

        assertThat(policy.canAccess(asset, scope(false, Set.of(), Set.of(),
                Set.of(), Set.of("ast_1")))).isTrue();
    }

    @Test
    void nullScopeDeniesNonPublicAssetAndNullAssetIsDenied() {
        assertThat(policy.canAccess(asset(Visibility.PRIVATE, "org_a", "prj_a", "team_a"), null))
                .isFalse();
        assertThat(policy.canAccess(asset(Visibility.INTERNAL, "org_a", "prj_a", "team_a"), null))
                .isFalse();
        assertThat(policy.canAccess(null, scope(false, Set.of(), Set.of(), Set.of(), Set.of())))
                .isFalse();
    }

    @Test
    void platformAdminBypassesVisibilityConditions() {
        Asset asset = asset(Visibility.PRIVATE, "org_b", "prj_b", "team_b");

        assertThat(policy.canAccess(asset, scope(true, Set.of(), Set.of(), Set.of(), Set.of())))
                .isTrue();
    }

    @Test
    void visibleVisibilitiesNarrowsByScopeInsteadOfReturningAll() {
        // 无任何作用域时不得宣称可见 INTERNAL/PRIVATE（防回归：旧实现恒返回三值）。
        assertThat(policy.visibleVisibilities(scope(false, Set.of(), Set.of(), Set.of(), Set.of())))
                .containsExactly(Visibility.PUBLIC);

        assertThat(policy.visibleVisibilities(scope(false, Set.of("org_a"), Set.of(),
                Set.of(), Set.of())))
                .containsExactlyInAnyOrder(Visibility.PUBLIC, Visibility.INTERNAL);

        assertThat(policy.visibleVisibilities(scope(false, Set.of(), Set.of(),
                Set.of("team_a"), Set.of())))
                .containsExactlyInAnyOrder(Visibility.PUBLIC, Visibility.PRIVATE);
    }

    @Test
    void assetStatusDoesNotAffectVisibilityMatrix() {
        // 状态门禁由检索条件与状态机负责，可见性矩阵只表达授权边界。
        Asset asset = asset(Visibility.PUBLIC, "org_a", "prj_a", "team_a");
        assertThat(asset.status()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(policy.canAccess(asset, scope(false, Set.of(), Set.of(), Set.of(), Set.of())))
                .isTrue();
    }
}

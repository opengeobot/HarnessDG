/*
 * 功能: Facet 过滤下推一致性集成测试——以真实 PostgreSQL 验证 Facet 计数与同条件检索一致，
 *       并验证授权隔离（不可见资产不进入任何维度计数）。
 * 时间: 2026-09-20
 * 作者: AxeXie
 */
package com.aihub.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetFacetView;
import com.aihub.asset.application.AssetSearchQuery;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.application.CreateAssetCommand;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.DatasetProfile;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.asset.infrastructure.RepositoryProvisionJobHandler;
import com.aihub.bootstrap.AihubApplication;
import com.aihub.identity.application.IdentityCommands.CreateUserCommand;
import com.aihub.identity.application.UserManagementApplicationService;
import com.aihub.identity.application.UserView;
import com.aihub.job.domain.JobContext;
import com.aihub.organization.application.OrganizationApplicationService;
import com.aihub.organization.application.OrganizationDtos.AddMemberCommand;
import com.aihub.organization.application.OrganizationDtos.CreateOrganizationCommand;
import com.aihub.organization.application.TeamApplicationService;
import com.aihub.organization.application.TeamDtos.CreateTeamCommand;
import com.aihub.organization.domain.MemberRole;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.taxonomy.tag.application.TagApplicationService;
import com.aihub.taxonomy.tag.application.TagDtos.CreateTagCommand;
import com.aihub.taxonomy.tag.application.TagDtos.TagView;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Facet 过滤下推一致性集成测试（{@code AC-DST-TAX-002} / {@code AC-P0B-AUTH-003}）。
 *
 * <p>核心不变量：**Facet 各维度计数 == 施加同一过滤条件后 search 的可达总数**。
 * 该不变量只能在真实 PostgreSQL 上验证，因为过滤必须在 SQL {@code WHERE} 阶段下推；
 * 若聚合忽略某个过滤位，计数会大于检索结果而在此暴露。
 *
 * <p>每个用例使用独立 {@code namespace} 作为夹具作用域，使绝对计数不受同容器内其他用例残留数据影响。
 *
 * <p>显式声明 {@link AihubApplication} 作为配置类：本测试位于 {@code com.aihub.asset} 包，
 * Spring Boot 的 {@code @SpringBootConfiguration} 向上搜索无法到达 {@code com.aihub.bootstrap}，
 * 不显式声明会导致上下文启动失败而非跳过（既有同类 IT 的共性问题，由独立任务治理）。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AihubApplication.class)
@ActiveProfiles("test")
class AssetFacetConsistencyIT {

    private static final String PASSWORD = "Sup3rSecret!23";

    /** 可用于审计 actorId 的任意字符串（非登录主体）。 */
    private static final String ACTOR = "usr_facet_actor";

    private static String organizationId;
    private static String teamId;
    /** 组织成员的内部主体：用于验证 INTERNAL 行级可见性（刻意不授予平台管理员）。 */
    private static String memberId;
    /** 非组织成员主体：用于验证不可见资产不进入任何维度计数。 */
    private static String outsiderId;

    /** 本用例的夹具命名空间（合法 slug），保证计数确定性。 */
    private String ns;
    /** 当前用例的主体内，供查询构造使用。 */
    private String currentPrincipalId;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AssetApplicationService assetService;
    @Autowired
    private TagApplicationService tagService;
    @Autowired
    private OrganizationApplicationService organizationService;
    @Autowired
    private TeamApplicationService teamService;
    @Autowired
    private UserManagementApplicationService userService;
    @Autowired
    private RepositoryProvisionJobHandler provisionHandler;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 惰性准备真实组织、Team 与主体。
     *
     * <p>惰性而非 {@code @BeforeAll}：{@code @DynamicPropertySource} 在 Spring 解析数据源时求值，
     * 与之同阶段的初始化逻辑会看到尚未启动的容器（Testcontainers 生命周期约束）。
     *
     * <p>{@code asset.owner_team_id} 有外键约束，因此必须存在真实 Team；
     * 组织成员关系是 INTERNAL 可见性的唯一来源，因此成员主体必须真实落库。
     */
    private void ensureFixtures() {
        if (organizationId != null) {
            return;
        }
        organizationId = organizationService.createOrganization(
                new CreateOrganizationCommand("facet-it-org", "Facet IT 组织", null), ACTOR).organizationId();
        teamId = teamService.createTeam(
                new CreateTeamCommand(organizationId, "Facet IT Team", null), ACTOR).teamId();

        memberId = createUser("facet_it_member", Set.of("asset:read", "asset:manage"));
        organizationService.addMember(new AddMemberCommand(organizationId, memberId),
                MemberRole.MEMBER, ACTOR);

        // 刻意不加入组织：访问作用域收窄到 PUBLIC，用于验证授权隔离。
        outsiderId = createUser("facet_it_outsider", Set.of("asset:read"));
    }

    private String createUser(String username, Set<String> scopes) {
        UserView user = userService.createUser(new CreateUserCommand(username, username + " 显示名",
                null, "zh-CN", PASSWORD, scopes));
        userService.enableUser(user.userId());
        return user.principalId();
    }

    @AfterEach
    void clearState() {
        PrincipalContextHolder.clear();
        ns = null;
        currentPrincipalId = null;
    }

    /**
     * 组织成员主体：具备 {@code asset:read}/{@code asset:manage}，
     * 但**不含** {@code authorization:manage}，因此不是平台管理员，
     * 可见性必须经真实的组织成员关系与行级 INTERNAL 过滤得出。
     */
    private void asMember() {
        currentPrincipalId = memberId;
        PrincipalContextHolder.set(new PrincipalContext(memberId, PrincipalType.USER, null, null,
                List.of(), Set.of(), Set.of("asset:read", "asset:manage"),
                0, "zh-CN", "req_facet_it", "trace_facet_it"));
    }

    /** 非组织成员主体：无组织/项目/ACL/Team 作用域，可见范围收窄到 PUBLIC。 */
    private void asOutsider() {
        currentPrincipalId = outsiderId;
        PrincipalContextHolder.set(new PrincipalContext(outsiderId, PrincipalType.USER, null, null,
                List.of(), Set.of(), Set.of("asset:read"),
                0, "zh-CN", "req_facet_it", "trace_facet_it"));
    }

    /** 以本用例命名空间为基准构造查询。 */
    private AssetSearchQuery query(String sensitivity, String tagId) {
        return new AssetSearchQuery(null, null, ns, null, null, null, null, null, tagId, null,
                null, sensitivity, false, null, 0, currentPrincipalId);
    }

    private AssetSearchQuery queryWithType(AssetType type) {
        return new AssetSearchQuery(null, type, ns, null, null, null, null, null, null, null,
                null, null, false, null, 0, currentPrincipalId);
    }

    /** 指定本用例作用域并准备夹具。 */
    private void useScope(String scope) {
        ns = scope;
        ensureFixtures();
    }

    private CreateAssetCommand modelCommand(String name, String framework, Visibility visibility) {
        return modelCommand(name, framework, visibility, null);
    }

    private CreateAssetCommand modelCommand(String name, String framework, Visibility visibility,
                                            String sensitivity) {
        return new CreateAssetCommand(AssetType.MODEL, organizationId, null, ns, name, name + " 展示名",
                "领域问答模型", visibility, List.of("team-nlp"),
                List.of("text-generation"), null, "MIT", teamId,
                new ModelProfile(framework, "TEXT_GENERATION", null, null, null, null, null, null,
                        null, sensitivity),
                null, ACTOR);
    }

    private CreateAssetCommand datasetCommand(String name, String format, Visibility visibility) {
        return new CreateAssetCommand(AssetType.DATASET, organizationId, null, ns, name, name + " 展示名",
                "图像数据集", visibility, List.of("team-cv"),
                List.of("vision"), null, "MIT", teamId,
                null, new DatasetProfile(format, "IMAGE"), ACTOR);
    }

    /** 断言 Facet 计数与同条件 search 可达总数一致。 */
    private AssetFacetView assertFacetMatchesSearch(AssetSearchQuery query, long expectedTotal) {
        AssetFacetView facets = assetService.getAssetFacets(query);
        CursorPage<AssetSummaryView> page = assetService.searchAssets(query);
        assertThat(facets.totalCount())
                .as("Facet totalCount 必须等于同条件 search 的可达总数")
                .isEqualTo((long) page.items().size())
                .isEqualTo(expectedTotal);
        return facets;
    }

    // ── AC-DST-TAX-002：type 维度下推 ──────────────────────────────────────

    @Test
    void facetTypeFilterMatchesSearchAcrossModelAndDataset() {
        useScope("facet-type");
        asMember();
        assetService.createAsset(modelCommand("model-a", "PYTORCH", Visibility.PUBLIC));
        assetService.createAsset(datasetCommand("dataset-a", "PARQUET", Visibility.PUBLIC));
        completeProvisioning();

        // 未过滤：MODEL 与 DATASET 各一个桶。
        AssetFacetView all = assertFacetMatchesSearch(query(null, null), 2L);
        assertThat(all.types()).containsEntry("MODEL", 1L).containsEntry("DATASET", 1L);

        // 按 type 过滤：该桶计数等于过滤后的可达总数，且另一类型不再出现。
        AssetFacetView datasets = assertFacetMatchesSearch(queryWithType(AssetType.DATASET), 1L);
        assertThat(datasets.types()).containsEntry("DATASET", 1L).doesNotContainKey("MODEL");

        AssetFacetView models = assertFacetMatchesSearch(queryWithType(AssetType.MODEL), 1L);
        assertThat(models.types()).containsEntry("MODEL", 1L).doesNotContainKey("DATASET");
    }

    // ── AC-DST-TAX-002：sensitivity 维度下推 ──────────────────────────────

    @Test
    void facetSensitivityFilterMatchesSearch() {
        useScope("facet-sens");
        asMember();
        assetService.createAsset(modelCommand("sens-public", "PYTORCH", Visibility.PUBLIC, "PUBLIC"));
        assetService.createAsset(modelCommand("sens-secret", "PYTORCH", Visibility.PUBLIC, "SECRET"));
        completeProvisioning();

        assertFacetMatchesSearch(query(null, null), 2L);

        AssetFacetView secretOnly = assertFacetMatchesSearch(query("SECRET", null), 1L);
        assertThat(secretOnly.frameworks()).containsEntry("PYTORCH", 1L);

        AssetFacetView publicOnly = assertFacetMatchesSearch(query("PUBLIC", null), 1L);
        assertThat(publicOnly.frameworks()).containsEntry("PYTORCH", 1L);
    }

    // ── AC-DST-TAX-002：tagId 维度下推 ────────────────────────────────────

    @Test
    void facetTagFilterMatchesSearchAndUnknownTagYieldsZero() {
        useScope("facet-tag");
        asMember();
        TagView tag = tagService.createTag(new CreateTagCommand(TagScopeType.PLATFORM, null,
                "facet-it-tag", "Facet IT 标签", "tag.facet.it", null), ACTOR);
        assetService.createAsset(new CreateAssetCommand(AssetType.MODEL, organizationId, null, ns,
                "tagged-a", "带标签模型", "描述", Visibility.PUBLIC, List.of("team-nlp"),
                List.of("text-generation"), List.of(tag.tagId()), "MIT", teamId,
                new ModelProfile("PYTORCH", "TEXT_GENERATION", null), null, ACTOR));
        assetService.createAsset(modelCommand("untagged-a", "PYTORCH", Visibility.PUBLIC));
        completeProvisioning();

        assertFacetMatchesSearch(query(null, null), 2L);

        AssetFacetView tagged = assertFacetMatchesSearch(query(null, tag.tagId()), 1L);
        assertThat(tagged.types()).containsEntry("MODEL", 1L);

        // 不存在的 tagId：计数为 0，而不是回退全量。
        AssetFacetView unknown = assertFacetMatchesSearch(query(null, "tag_definitely_not_exist"), 0L);
        assertThat(unknown.types()).isEmpty();
        assertThat(unknown.frameworks()).isEmpty();
    }

    // ── AC-DST-TAX-002：框架与格式维度下推 ────────────────────────────────

    @Test
    void facetFrameworkAndFormatFiltersMatchSearch() {
        useScope("facet-fw");
        asMember();
        assetService.createAsset(modelCommand("fw-pytorch", "PYTORCH", Visibility.PUBLIC));
        assetService.createAsset(modelCommand("fw-tensorflow", "TENSORFLOW", Visibility.PUBLIC));
        assetService.createAsset(datasetCommand("fmt-parquet", "PARQUET", Visibility.PUBLIC));
        completeProvisioning();

        AssetSearchQuery pytorchOnly = new AssetSearchQuery(null, null, ns, null, "PYTORCH", null,
                null, null, null, null, null, null, false, null, 0, currentPrincipalId);
        AssetFacetView frameworks = assertFacetMatchesSearch(pytorchOnly, 1L);
        assertThat(frameworks.frameworks()).containsEntry("PYTORCH", 1L)
                .doesNotContainKey("TENSORFLOW");

        AssetSearchQuery parquetOnly = new AssetSearchQuery(null, null, ns, null, null, null,
                "PARQUET", null, null, null, null, null, false, null, 0, currentPrincipalId);
        AssetFacetView formats = assertFacetMatchesSearch(parquetOnly, 1L);
        assertThat(formats.formats()).containsEntry("PARQUET", 1L);
        // 数据集被过滤后，模型框架维度不应再计入模型资产。
        assertThat(formats.frameworks()).doesNotContainKey("PYTORCH");
    }

    // ── AC-DST-TAX-002：不同维度之间为 AND ────────────────────────────────

    @Test
    void facetFiltersCombineWithAndAcrossDimensions() {
        useScope("facet-and");
        asMember();
        assetService.createAsset(modelCommand("and-pytorch", "PYTORCH", Visibility.PUBLIC));
        assetService.createAsset(modelCommand("and-tensorflow", "TENSORFLOW", Visibility.PUBLIC));
        assetService.createAsset(datasetCommand("and-parquet", "PARQUET", Visibility.PUBLIC));
        completeProvisioning();

        assertFacetMatchesSearch(query(null, null), 3L);

        // type=DATASET 且 framework=PYTORCH 无交集 → 0（AND 语义），而非任一维度的并集。
        AssetSearchQuery contradictory = new AssetSearchQuery(null, AssetType.DATASET, ns, null,
                "PYTORCH", null, null, null, null, null, null, null, false, null, 0, currentPrincipalId);
        AssetFacetView facets = assertFacetMatchesSearch(contradictory, 0L);
        assertThat(facets.types()).isEmpty();
        assertThat(facets.frameworks()).isEmpty();
    }

    // ── AC-P0B-AUTH-003：授权隔离，不可见资产不进入计数 ──────────────────

    @Test
    void facetCountsAreIsolatedPerPrincipal() {
        useScope("facet-authz");
        asMember();
        // 同为本组织资产：一个 PUBLIC、一个 INTERNAL，使差异只来自行级可见性。
        assetService.createAsset(modelCommand("vis-public", "PYTORCH", Visibility.PUBLIC));
        assetService.createAsset(new CreateAssetCommand(AssetType.MODEL, organizationId, null, ns,
                "vis-internal", "内部模型", "描述", Visibility.INTERNAL, List.of("team-nlp"),
                List.of("text-generation"), null, "MIT", teamId,
                new ModelProfile("PYTORCH", "TEXT_GENERATION", null), null, ACTOR));
        completeProvisioning();

        // 组织成员：PUBLIC + 本组织 INTERNAL 均可见。
        AssetFacetView memberView = assertFacetMatchesSearch(query(null, null), 2L);
        assertThat(memberView.frameworks()).containsEntry("PYTORCH", 2L);

        // 非成员：只见 PUBLIC；INTERNAL 资产不得进入任何维度计数。
        asOutsider();
        AssetFacetView outsiderView = assertFacetMatchesSearch(query(null, null), 1L);
        assertThat(outsiderView.types())
                .as("不可见资产不得进入 Facet 维度（AC-P0B-AUTH-003）")
                .containsEntry("MODEL", 1L);
        assertThat(outsiderView.frameworks()).containsEntry("PYTORCH", 1L);
    }

    // ── AC-DST-TAX-002：ARCHIVED 默认不进入计数 ───────────────────────────

    @Test
    void archivedAssetsExcludedFromDefaultFacets() {
        useScope("facet-arch");
        asMember();
        AssetView archived = assetService.createAsset(
                modelCommand("arch-a", "PYTORCH", Visibility.PUBLIC));
        assetService.createAsset(modelCommand("active-a", "PYTORCH", Visibility.PUBLIC));
        completeProvisioning();

        assertFacetMatchesSearch(query(null, null), 2L);

        assetService.archiveAsset(archived.assetId(), ACTOR);

        // 默认：归档资产不进入 Facet，也不进入检索。
        assertFacetMatchesSearch(query(null, null), 1L);

        // includeArchived=true：两者都计入，且与检索一致。
        AssetSearchQuery withArchived = new AssetSearchQuery(null, null, ns, null, null, null, null,
                null, null, null, null, null, true, null, 0, currentPrincipalId);
        assertFacetMatchesSearch(withArchived, 2L);
    }

    // ── 失败路径：缺少 asset:read 时不得静默返回空 Facet ──────────────────

    @Test
    void facetWithoutReadPermissionFailsClosed() {
        useScope("facet-denied");
        // 仅有 project:view，且无任何 RBAC 绑定 → 不得返回空 Facet 冒充成功。
        currentPrincipalId = "usr_facet_no_permission";
        PrincipalContextHolder.set(new PrincipalContext(currentPrincipalId, PrincipalType.USER, null,
                null, List.of(), Set.of(), Set.of("project:view"),
                0, "zh-CN", "req_facet_it", "trace_facet_it"));

        assertThatThrownBy(() -> assetService.getAssetFacets(query(null, null)))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> assetService.searchAssets(query(null, null)))
                .isInstanceOf(AuthorizationException.class);
    }

    // ── 边界：未知受控过滤值返回 0，不回退全量 ────────────────────────────

    @Test
    void facetRejectsUnknownFilterValue() {
        useScope("facet-unknown");
        asMember();
        assetService.createAsset(modelCommand("unknown-a", "PYTORCH", Visibility.PUBLIC));
        completeProvisioning();
        assertFacetMatchesSearch(query(null, null), 1L);

        // 未知 sensitivity：有界等值过滤，结果为 0 而非回退全量。
        assertFacetMatchesSearch(query("NOT_A_LEVEL", null), 0L);
    }

    /**
     * 把本用例的待建仓资产推进到 {@code COMPLETED}。
     *
     * <p>检索与 Facet 都以 {@code provisioning_status = 'COMPLETED'} 为前提，而建仓由可靠任务异步完成；
     * 这里驱动真实的 {@link RepositoryProvisionJobHandler}（而非改库绕过），
     * 使测试经过与生产一致的建仓 Saga 路径。
     */
    private void completeProvisioning() {
        List<String> pending = jdbcTemplate.queryForList(
                "SELECT asset_id FROM asset WHERE provisioning_status = 'PENDING' AND namespace = ?",
                String.class, ns);
        assertThat(pending).as("夹具应包含待建仓资产").isNotEmpty();
        for (String assetId : pending) {
            try {
                provisionHandler.handle(new JobContext("job_facet_it", "REPOSITORY_PROVISION",
                        "{\"assetId\":\"" + assetId + "\"}", 0, ACTOR, "trace_facet_it", assetId));
            } catch (Exception ex) {
                throw new IllegalStateException("建仓 Saga 执行失败: " + assetId, ex);
            }
        }
    }
}

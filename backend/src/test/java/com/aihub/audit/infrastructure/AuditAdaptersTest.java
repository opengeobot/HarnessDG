/*
 * 功能: 全模块审计适配器单元测试——验证 4 个 AuditPort 适配器正确委托 AuditService。
 * 时间: 2026-07-03
 * 作者: AxeXie
 */
package com.aihub.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.infrastructure.AuthorizationAuditAdapter;
import com.aihub.configuration.infrastructure.ConfigurationAuditAdapter;
import com.aihub.organization.infrastructure.OrganizationAuditAdapter;
import com.aihub.taxonomy.infrastructure.TaxonomyAuditAdapter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 审计适配器集成测试——验证 4 个模块的 AuditPort 适配器均正确委托 AuditService。
 */
class AuditAdaptersTest {

    private AuditService auditService;
    private ConfigurationAuditAdapter configAdapter;
    private TaxonomyAuditAdapter taxonomyAdapter;
    private OrganizationAuditAdapter orgAdapter;
    private AuthorizationAuditAdapter authAdapter;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        configAdapter = new ConfigurationAuditAdapter(auditService);
        taxonomyAdapter = new TaxonomyAuditAdapter(auditService);
        orgAdapter = new OrganizationAuditAdapter(auditService);
        authAdapter = new AuthorizationAuditAdapter(auditService);
    }

    // ---- ConfigurationAuditAdapter ----

    @Test
    void configurationAdapter_delegatesToAuditService() {
        Map<String, Object> attrs = Map.of("configKey", "max_upload_size");
        configAdapter.record("CONFIG_UPDATED", "usr_001", "cfg_01", attrs);

        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assert event.eventType().equals("CONFIG_UPDATED");
        assert event.principalId().equals("usr_001");
        assert event.resourceId().equals("cfg_01");
        assert event.resourceType().equals("CONFIG");
        assert event.result() == AuditResult.SUCCEEDED;
        assert event.attributes().equals(attrs);
    }

    @Test
    void configurationAdapter_swallowsException() {
        doThrow(new RuntimeException("db down")).when(auditService).record(any());
        assertThatCode(() -> configAdapter.record("CONFIG_UPDATED", "usr_001", "cfg_01", Map.of()))
                .doesNotThrowAnyException();
    }

    // ---- TaxonomyAuditAdapter ----

    @Test
    void taxonomyAdapter_delegatesToAuditService() {
        Map<String, Object> attrs = Map.of("dictType", "LICENSE");
        taxonomyAdapter.record("DICTIONARY_ITEM_CREATED", "usr_002", "di_01", attrs);

        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assert event.eventType().equals("DICTIONARY_ITEM_CREATED");
        assert event.principalId().equals("usr_002");
        assert event.resourceId().equals("di_01");
        assert event.result() == AuditResult.SUCCEEDED;
    }

    @Test
    void taxonomyAdapter_swallowsException() {
        doThrow(new RuntimeException("db down")).when(auditService).record(any());
        assertThatCode(() -> taxonomyAdapter.record("TAG_CREATED", "usr_002", "tag_01", Map.of()))
                .doesNotThrowAnyException();
    }

    // ---- OrganizationAuditAdapter ----

    @Test
    void organizationAdapter_delegatesToAuditService() {
        Map<String, Object> attrs = Map.of("orgName", "TestOrg");
        orgAdapter.record("ORGANIZATION_CREATED", "usr_003", "org_01", attrs);

        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assert event.eventType().equals("ORGANIZATION_CREATED");
        assert event.principalId().equals("usr_003");
        assert event.resourceId().equals("org_01");
        assert event.result() == AuditResult.SUCCEEDED;
    }

    @Test
    void organizationAdapter_swallowsException() {
        doThrow(new RuntimeException("db down")).when(auditService).record(any());
        assertThatCode(() -> orgAdapter.record("PROJECT_UPDATED", "usr_003", "prj_01", Map.of()))
                .doesNotThrowAnyException();
    }

    // ---- AuthorizationAuditAdapter ----

    @Test
    void authorizationAdapter_delegatesToAuditService() {
        Map<String, Object> attrs = Map.of("roleName", "ADMIN");
        authAdapter.record("ROLE_CREATED", "usr_004", "role_01", attrs);

        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assert event.eventType().equals("ROLE_CREATED");
        assert event.principalId().equals("usr_004");
        assert event.resourceId().equals("role_01");
        assert event.result() == AuditResult.SUCCEEDED;
    }

    @Test
    void authorizationAdapter_swallowsException() {
        doThrow(new RuntimeException("db down")).when(auditService).record(any());
        assertThatCode(() -> authAdapter.record("ROLE_DELETED", "usr_004", "role_01", Map.of()))
                .doesNotThrowAnyException();
    }
}

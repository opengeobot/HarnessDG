/*
 * 功能: ResourceAclApplicationService 单元测试——ACL 创建/列表/删除、权限编码校验、冲突拒绝、审计记录。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.authorization.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.authorization.application.AuthorizationDtos.CreateResourceAclCommand;
import com.aihub.authorization.application.AuthorizationDtos.ResourceAclView;
import com.aihub.authorization.domain.PermissionRepository;
import com.aihub.authorization.domain.ResourceAcl;
import com.aihub.authorization.domain.ResourceAclRepository;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ResourceAclApplicationService 单元测试。
 *
 * <p>覆盖 ACL 创建（含权限编码校验、冲突拒绝、审计）、列表、删除（含 404）路径。
 */
class ResourceAclApplicationServiceTest {

    private ResourceAclRepository resourceAclRepository;
    private PermissionRepository permissionRepository;
    private IdGenerator idGenerator;
    private AuditPort auditPort;
    private Clock clock;
    private ResourceAclApplicationService service;

    private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");

    @BeforeEach
    void setUp() {
        resourceAclRepository = mock(ResourceAclRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        idGenerator = mock(IdGenerator.class);
        auditPort = mock(AuditPort.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ResourceAclApplicationService(
                resourceAclRepository, permissionRepository, idGenerator, auditPort, clock);
    }

    @Test
    void createAclSuccessAndRecordsAudit() {
        when(idGenerator.generate(IdPrefix.RESOURCE_ACL)).thenReturn("racl_001");
        when(permissionRepository.findUnknownCodes(List.of("ASSET_READ"))).thenReturn(List.of());
        when(resourceAclRepository.exists("ASSET", "ast_1", "prn_user", "ASSET_READ")).thenReturn(false);

        // 使用 PrincipalContextHolder 包装执行
        PrincipalContext ctx = new PrincipalContext("prn_admin", PrincipalType.USER, null, null,
                List.of(), Set.of(), Set.of(), 0, "zh-CN", "req_1", "trace_1");

        CreateResourceAclCommand cmd = new CreateResourceAclCommand(
                "prn_user", "ASSET", "ast_1", List.of("ASSET_READ"));

        PrincipalContextHolder.set(ctx);
        ResourceAclView result;
        try {
            result = service.createAcl(cmd);
        } finally {
            PrincipalContextHolder.clear();
        }

        assertEquals("racl_001", result.aclId());
        assertEquals("prn_user", result.principalId());
        assertEquals("ASSET", result.resourceType());
        assertEquals("ast_1", result.resourceId());
        assertTrue(result.permissionCodes().contains("ASSET_READ"));
        assertEquals(NOW, result.createdAt());

        verify(resourceAclRepository).create(any(ResourceAcl.class));
        verify(auditPort).record(eq("RESOURCE_ACL_CREATED"), eq("prn_admin"), eq("racl_001"), anyMap());
    }

    @Test
    void createAclRejectsUnknownPermissionCodes() {
        when(permissionRepository.findUnknownCodes(List.of("FAKE_PERM")))
                .thenReturn(List.of("FAKE_PERM"));

        CreateResourceAclCommand cmd = new CreateResourceAclCommand(
                "prn_user", "ASSET", "ast_1", List.of("FAKE_PERM"));

        assertThrows(ValidationException.class, () -> service.createAcl(cmd));
        verify(resourceAclRepository, never()).create(any());
    }

    @Test
    void createAclRejectsDuplicateAcl() {
        when(permissionRepository.findUnknownCodes(List.of("ASSET_READ"))).thenReturn(List.of());
        when(resourceAclRepository.exists("ASSET", "ast_1", "prn_user", "ASSET_READ")).thenReturn(true);

        CreateResourceAclCommand cmd = new CreateResourceAclCommand(
                "prn_user", "ASSET", "ast_1", List.of("ASSET_READ"));

        assertThrows(ConflictException.class, () -> service.createAcl(cmd));
    }

    @Test
    void createAclRejectsBlankPrincipalId() {
        CreateResourceAclCommand cmd = new CreateResourceAclCommand(
                "", "ASSET", "ast_1", List.of("ASSET_READ"));

        assertThrows(ValidationException.class, () -> service.createAcl(cmd));
    }

    @Test
    void createAclRejectsBlankResourceType() {
        CreateResourceAclCommand cmd = new CreateResourceAclCommand(
                "prn_user", "", "ast_1", List.of("ASSET_READ"));

        assertThrows(ValidationException.class, () -> service.createAcl(cmd));
    }

    @Test
    void createAclRejectsBlankResourceId() {
        CreateResourceAclCommand cmd = new CreateResourceAclCommand(
                "prn_user", "ASSET", "", List.of("ASSET_READ"));

        assertThrows(ValidationException.class, () -> service.createAcl(cmd));
    }

    @Test
    void createAclRejectsEmptyPermissionCodes() {
        CreateResourceAclCommand cmd = new CreateResourceAclCommand(
                "prn_user", "ASSET", "ast_1", List.of());

        assertThrows(ValidationException.class, () -> service.createAcl(cmd));
    }

    @Test
    void listAclsDelegatesToRepository() {
        ResourceAcl acl = new ResourceAcl("racl_1", "ASSET", "ast_1", "prn_user",
                Set.of("ASSET_READ"), "prn_admin", NOW);
        when(resourceAclRepository.findAll()).thenReturn(List.of(acl));

        List<ResourceAclView> result = service.listAcls();

        assertEquals(1, result.size());
        assertEquals("racl_1", result.get(0).aclId());
    }

    @Test
    void listAclsByResourceRejectsBlankResourceType() {
        assertThrows(ValidationException.class, () -> service.listAclsByResource("", "ast_1"));
    }

    @Test
    void listAclsByResourceRejectsBlankResourceId() {
        assertThrows(ValidationException.class, () -> service.listAclsByResource("ASSET", ""));
    }

    @Test
    void deleteAclSuccessAndRecordsAudit() {
        ResourceAcl acl = new ResourceAcl("racl_1", "ASSET", "ast_1", "prn_user",
                Set.of("ASSET_READ"), "prn_admin", NOW);
        when(resourceAclRepository.findByAclId("racl_1")).thenReturn(Optional.of(acl));

        PrincipalContext ctx = new PrincipalContext("prn_admin", PrincipalType.USER, null, null,
                List.of(), Set.of(), Set.of(), 0, "zh-CN", "req_1", "trace_1");
        PrincipalContextHolder.set(ctx);
        try {
            service.deleteAcl("racl_1");
        } finally {
            PrincipalContextHolder.clear();
        }

        verify(resourceAclRepository).deleteByAclId("racl_1");
        verify(auditPort).record(eq("RESOURCE_ACL_DELETED"), eq("prn_admin"), eq("racl_1"), anyMap());
    }

    @Test
    void deleteAclThrowsNotFoundWhenAbsent() {
        when(resourceAclRepository.findByAclId("racl_missing")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.deleteAcl("racl_missing"));
        verify(resourceAclRepository, never()).deleteByAclId(anyString());
    }
}

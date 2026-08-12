package com.modelhub.identity.bootstrap;

import com.modelhub.identity.config.BootstrapProperties;
import com.modelhub.identity.repo.NamespaceRepository;
import com.modelhub.identity.repo.PlatformRoleAssignmentRepository;
import com.modelhub.identity.repo.UserRepository;
import com.modelhub.identity.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * bootstrap 约束单元测试（02 §2）：禁止 username==demo；已有平台角色时不做任何事。
 */
class BootstrapAdminRunnerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final NamespaceRepository namespaces = mock(NamespaceRepository.class);
    private final PlatformRoleAssignmentRepository roles = mock(PlatformRoleAssignmentRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    private BootstrapAdminRunner runner(BootstrapProperties props) {
        return new BootstrapAdminRunner(props, users, namespaces, roles,
                new BCryptPasswordEncoder(4), auditService);
    }

    @Test
    void demo_username_is_rejected() {
        when(roles.countByRevokedAtIsNull()).thenReturn(0L);
        BootstrapAdminRunner runner = runner(new BootstrapProperties(true, "demo", "Passw0rd-x"));
        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo");
    }

    @Test
    void does_nothing_when_platform_role_exists() throws Exception {
        when(roles.countByRevokedAtIsNull()).thenReturn(1L);
        runner(new BootstrapProperties(true, "demo", "Passw0rd-x")).run(null);
        verifyNoInteractions(users, namespaces);
    }

    @Test
    void does_nothing_when_disabled() throws Exception {
        when(roles.countByRevokedAtIsNull()).thenReturn(0L);
        runner(new BootstrapProperties(false, "", "")).run(null);
        verifyNoInteractions(users, namespaces);
    }
}

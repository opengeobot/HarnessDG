package com.modelhub.identity.bootstrap;

import com.modelhub.identity.config.BootstrapProperties;
import com.modelhub.identity.domain.NamespaceEntity;
import com.modelhub.identity.domain.PlatformRoleAssignmentEntity;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.NamespaceRepository;
import com.modelhub.identity.repo.PlatformRoleAssignmentRepository;
import com.modelhub.identity.repo.UserRepository;
import com.modelhub.identity.service.AuditService;
import com.modelhub.shared.id.PublicIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * bootstrap（02 §2）：平台无任何有效平台角色时，创建首个 platform_admin 并写入审计。
 * 明确禁止 username == demo；已存在有效角色时不做任何事（无长期旁路授权）。
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final BootstrapProperties props;
    private final UserRepository users;
    private final NamespaceRepository namespaces;
    private final PlatformRoleAssignmentRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public BootstrapAdminRunner(BootstrapProperties props, UserRepository users,
                                NamespaceRepository namespaces, PlatformRoleAssignmentRepository roles,
                                PasswordEncoder passwordEncoder, AuditService auditService) {
        this.props = props;
        this.users = users;
        this.namespaces = namespaces;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (roles.countByRevokedAtIsNull() > 0) {
            return;
        }
        if (!props.enabled() || props.username().isBlank() || props.password().isBlank()) {
            log.warn("平台尚无 platform_admin 且 bootstrap 未配置；请通过 modelhub.bootstrap.* 创建首个管理员");
            return;
        }
        String username = props.username().trim().toLowerCase(Locale.ROOT);
        if ("demo".equals(username)) {
            throw new IllegalStateException("bootstrap 管理员禁止使用 username == demo（02 §2）");
        }
        UserEntity user = users.findByUsername(username).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setPublicId(PublicIds.next());
            u.setUsername(username);
            u.setPasswordHash(passwordEncoder.encode(props.password()));
            u.setNickname(username);
            users.save(u);
            NamespaceEntity ns = new NamespaceEntity();
            ns.setPublicId(PublicIds.next());
            ns.setNamespaceType("user");
            ns.setUserId(u.getId());
            ns.setSlug(username);
            ns.setDisplayName(username);
            namespaces.save(ns);
            return u;
        });
        PlatformRoleAssignmentEntity assignment = new PlatformRoleAssignmentEntity();
        assignment.setUserId(user.getId());
        assignment.setRole("platform_admin");
        roles.save(assignment);
        auditService.appendSimple("bootstrap", "bootstrap.platform_admin",
                "user:" + user.getPublicId(), "success");
        log.info("bootstrap: 已创建首个 platform_admin（{}）并写入审计", username);
    }
}

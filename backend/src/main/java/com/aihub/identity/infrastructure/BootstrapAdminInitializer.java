/*
 * 功能: 首个管理员引导器——启动时若无任何管理员则按配置创建一个强制改密的初始管理员。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.aihub.identity.application.IdentityScopes;
import com.aihub.identity.domain.LocalUser;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.identity.domain.PrincipalAccount;
import com.aihub.identity.domain.UserStatus;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.PasswordHasher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 首个管理员引导器。
 *
 * <p>启动时若已存在持有 {@code user:manage} Scope 的活跃用户则跳过；否则按配置创建初始管理员，
 * 赋予基础管理 Scope 并强制下次修改口令。口令绝不硬编码/写日志；未配置或文件读取失败时仅打印提示，不报错。
 */
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final LocalUserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public BootstrapAdminInitializer(BootstrapAdminProperties properties,
                                     LocalUserRepository userRepository,
                                     PasswordHasher passwordHasher,
                                     IdGenerator idGenerator,
                                     Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.countActiveByScope(IdentityScopes.USER_MANAGE) > 0) {
            return;
        }
        String username = properties.username();
        if (username == null || username.isBlank()) {
            LOG.info("No bootstrap admin configured (aihub.bootstrap.admin.username is empty); "
                    + "skipping initial admin creation. Configure it to provision the first administrator.");
            return;
        }
        if (userRepository.existsByUsername(username)) {
            LOG.info("Bootstrap admin username already exists; skipping initial admin creation.");
            return;
        }
        char[] password = resolvePassword();
        if (password == null || password.length == 0) {
            LOG.info("Bootstrap admin username configured but no password source provided "
                    + "(aihub.bootstrap.admin.password / password-file); skipping initial admin creation.");
            return;
        }

        try {
            Instant now = clock.instant();
            String principalId = idGenerator.generate(IdPrefix.PRINCIPAL);
            String userId = idGenerator.generate(IdPrefix.USER);
            PrincipalAccount principal = new PrincipalAccount(
                    principalId, PrincipalType.USER, properties.displayName(), "ACTIVE", now, now);
            LocalUser admin = new LocalUser.Builder()
                    .userId(userId)
                    .principalId(principalId)
                    .username(username)
                    .displayName(properties.displayName())
                    .locale("zh-CN")
                    .passwordHash(passwordHasher.hash(new String(password)))
                    .scopes(IdentityScopes.ADMIN_SCOPES)
                    .tokenVersion(0L)
                    .mustChangePassword(true)
                    .status(UserStatus.PENDING_ACTIVATION)
                    .createdAt(now)
                    .updatedAt(now)
                    .rowVersion(0L)
                    .build();
            userRepository.create(principal, admin);
            // 仅记录用户名，绝不记录口令。
            LOG.info("Bootstrap admin '{}' created; password change is required on first login.", username);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private char[] resolvePassword() {
        if (properties.passwordFile() != null && !properties.passwordFile().isBlank()) {
            try {
                String content = Files.readString(Path.of(properties.passwordFile())).strip();
                if (!content.isEmpty()) {
                    return content.toCharArray();
                }
            } catch (Exception ex) {
                LOG.warn("Failed to read bootstrap admin password file; skipping initial admin creation.");
                return null;
            }
        }
        if (properties.password() != null && !properties.password().isBlank()) {
            return properties.password().toCharArray();
        }
        return null;
    }
}

package com.modelhub.identity.service;

import com.modelhub.identity.config.IdentityProperties;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 登录失败计数与账户锁定（02 §6）。
 * 使用 REQUIRES_NEW 独立事务：登录失败会抛出异常导致外层事务回滚，
 * 失败计数与锁定状态必须独立提交才能生效。
 */
@Component
public class LoginGuard {

    private final UserRepository users;
    private final AuditService auditService;
    private final IdentityProperties props;

    public LoginGuard(UserRepository users, AuditService auditService, IdentityProperties props) {
        this.users = users;
        this.auditService = auditService;
        this.props = props;
    }

    /**
     * 记录一次登录失败：写 auth.login_fail 审计；用户存在时累计失败计数，
     * 达到阈值即锁定账户。全部在独立事务提交，不随外层抛异常回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(String username, UserEntity user, String ip, String userAgent) {
        if (user != null) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= props.session().maxFailedAttempts()) {
                user.setStatus("locked");
                auditService.append(user.getUsername(), "account.locked", "user:" + user.getPublicId(),
                        "locked", ip, userAgent, null);
            }
            user.setUpdatedAt(OffsetDateTime.now());
            users.save(user);
        }
        auditService.append(username, "auth.login_fail",
                user == null ? null : "user:" + user.getPublicId(), "denied", ip, userAgent, null);
    }
}

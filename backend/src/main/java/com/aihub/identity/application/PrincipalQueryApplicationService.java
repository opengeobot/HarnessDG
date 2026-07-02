/*
 * 功能: 主体查询应用服务，编排统一主体检索用例。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.identity.domain.PrincipalQueryRepository;
import com.aihub.shared.identity.PrincipalType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主体查询应用服务。
 */
@Service
public class PrincipalQueryApplicationService {

    private final PrincipalQueryRepository principalQueryRepository;

    public PrincipalQueryApplicationService(PrincipalQueryRepository principalQueryRepository) {
        this.principalQueryRepository = principalQueryRepository;
    }

    /**
     * 按可选类型与关键字检索主体摘要。
     */
    @Transactional(readOnly = true)
    public List<PrincipalSummaryView> listPrincipals(PrincipalType principalType, String keyword) {
        return principalQueryRepository.search(principalType, keyword).stream()
                .map(account -> new PrincipalSummaryView(
                        account.principalId(),
                        account.principalType(),
                        account.principalId(),
                        account.displayName(),
                        account.status()))
                .toList();
    }

    /**
     * 判断给定主体 ID 是否存在。
     *
     * @param principalId 主体 ID
     * @return 是否存在
     */
    @Transactional(readOnly = true)
    public boolean existsByPrincipalId(String principalId) {
        return principalQueryRepository.existsByPrincipalId(principalId);
    }
}

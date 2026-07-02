/*
 * 功能: 主体查询适配器，基于 MyBatis-Plus 实现统一主体的只读检索。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.aihub.identity.domain.PrincipalAccount;
import com.aihub.identity.domain.PrincipalQueryRepository;
import com.aihub.shared.identity.PrincipalType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 主体查询适配器。
 */
@Repository
public class MyBatisPrincipalQueryRepository implements PrincipalQueryRepository {

    private final PrincipalMapper principalMapper;

    public MyBatisPrincipalQueryRepository(PrincipalMapper principalMapper) {
        this.principalMapper = principalMapper;
    }

    @Override
    public List<PrincipalAccount> search(PrincipalType principalType, String keyword) {
        LambdaQueryWrapper<PrincipalEntity> wrapper = Wrappers.<PrincipalEntity>lambdaQuery()
                .orderByDesc(PrincipalEntity::getCreatedAt);
        if (principalType != null) {
            wrapper.eq(PrincipalEntity::getPrincipalType, principalType.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(w -> w.like(PrincipalEntity::getDisplayName, like)
                    .or().like(PrincipalEntity::getPrincipalId, like));
        }
        return principalMapper.selectList(wrapper).stream()
                .map(entity -> new PrincipalAccount(
                        entity.getPrincipalId(),
                        PrincipalType.valueOf(entity.getPrincipalType()),
                        entity.getDisplayName(),
                        entity.getStatus(),
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()))
                .toList();
    }

    @Override
    public boolean existsByPrincipalId(String principalId) {
        if (principalId == null || principalId.isBlank()) {
            return false;
        }
        Long count = principalMapper.selectCount(Wrappers.<PrincipalEntity>lambdaQuery()
                .eq(PrincipalEntity::getPrincipalId, principalId));
        return count != null && count > 0;
    }
}

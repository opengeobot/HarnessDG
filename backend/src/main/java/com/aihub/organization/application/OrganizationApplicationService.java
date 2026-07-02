/*
 * 功能: 组织应用服务，编排组织与组织成员的查询、创建与移除用例（含成员隔离下推查询）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.application;

import com.aihub.identity.application.PrincipalQueryApplicationService;
import com.aihub.organization.application.OrganizationDtos.AddMemberCommand;
import com.aihub.organization.application.OrganizationDtos.CreateOrganizationCommand;
import com.aihub.organization.application.OrganizationDtos.OrganizationMemberView;
import com.aihub.organization.application.OrganizationDtos.OrganizationView;
import com.aihub.organization.domain.MemberRole;
import com.aihub.organization.domain.Organization;
import com.aihub.organization.domain.OrganizationMember;
import com.aihub.organization.domain.OrganizationMemberRepository;
import com.aihub.organization.domain.OrganizationRepository;
import com.aihub.organization.domain.OrganizationStatus;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织应用服务。
 *
 * <p>编排组织与成员用例：组织列表（成员隔离下推）、创建组织、列出/添加/移除成员。
 * <b>不依赖 authorization 模块</b>（避免与 authorization→organization 的成员作用域查询端口形成循环依赖）；
 * 平台组织管理员的判定由适配层（Controller）经 AuthorizationService 完成后以 {@code platformAdmin} 入参传入，
 * 应用层据此选择"全量"或"按成员关系下推"查询，均由数据库阶段过滤，杜绝"先全量再 Java 过滤"。
 *
 * <p>防资源枚举：组织不存在与主体非成员对外统一返回 {@code ORGANIZATION_NOT_FOUND}。
 */
@Service
public class OrganizationApplicationService {

    /** 组织/项目编码格式：小写字母/数字/连字符，3~64 位，首尾非连字符。 */
    static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$");

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PrincipalQueryApplicationService principalQuery;
    private final IdGenerator idGenerator;
    private final AuditPort auditPort;
    private final Clock clock;

    public OrganizationApplicationService(OrganizationRepository organizationRepository,
                                          OrganizationMemberRepository memberRepository,
                                          PrincipalQueryApplicationService principalQuery,
                                          IdGenerator idGenerator,
                                          AuditPort auditPort,
                                          Clock clock) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.principalQuery = principalQuery;
        this.idGenerator = idGenerator;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    /**
     * 查询组织列表（成员隔离）。
     *
     * @param principalId   当前主体 ID
     * @param platformAdmin 是否平台组织管理员（可见全部）
     * @return 组织视图列表
     */
    @Transactional(readOnly = true)
    public List<OrganizationView> listOrganizations(String principalId, boolean platformAdmin) {
        List<Organization> organizations = platformAdmin
                ? organizationRepository.findAll()
                : organizationRepository.findByPrincipalMembership(principalId);
        return organizations.stream().map(OrganizationView::from).toList();
    }

    /**
     * 创建组织。
     */
    @Transactional
    public OrganizationView createOrganization(CreateOrganizationCommand command, String actorId) {
        validateCode(command.code());
        validateName(command.name());
        if (organizationRepository.existsByCode(command.code())) {
            throw new ConflictException(ErrorCode.ORGANIZATION_ALREADY_EXISTS,
                    "organization code already exists", Map.of("code", command.code()));
        }
        Organization organization = new Organization(
                idGenerator.generate(IdPrefix.ORGANIZATION),
                command.code().trim(),
                command.name().trim(),
                null,
                command.giteaOrganization(),
                OrganizationStatus.ACTIVE,
                actorId,
                clock.instant(),
                clock.instant(),
                0);
        organizationRepository.insert(organization);
        auditPort.record("ORGANIZATION_CREATED", actorId, organization.organizationId(),
                Map.of("code", organization.code(), "name", organization.name()));
        return OrganizationView.from(organization);
    }

    /**
     * 列出组织成员。
     */
    @Transactional(readOnly = true)
    public List<OrganizationMemberView> listMembers(String organizationId) {
        requireOrganizationExists(organizationId);
        return memberRepository.findByOrganizationId(organizationId).stream()
                .map(OrganizationMemberView::from).toList();
    }

    /**
     * 添加组织成员。
     */
    @Transactional
    public OrganizationMemberView addMember(AddMemberCommand command, MemberRole role, String actorId) {
        String organizationId = command.organizationId();
        String principalId = command.principalId();
        requireOrganizationExists(organizationId);
        if (!principalQuery.existsByPrincipalId(principalId)) {
            throw new NotFoundException(ErrorCode.PRINCIPAL_NOT_FOUND,
                    "principal not found", Map.of("principalId", principalId));
        }
        if (memberRepository.exists(organizationId, principalId)) {
            throw new ConflictException(ErrorCode.ORGANIZATION_MEMBER_ALREADY_EXISTS,
                    "organization member already exists",
                    Map.of("organizationId", organizationId, "principalId", principalId));
        }
        OrganizationMember member = new OrganizationMember(organizationId, principalId, role, actorId,
                clock.instant());
        memberRepository.add(member);
        auditPort.record("ORGANIZATION_MEMBER_ADDED", actorId, organizationId,
                Map.of("principalId", principalId, "role", role.name()));
        return OrganizationMemberView.from(member);
    }

    /**
     * 移除组织成员。
     */
    @Transactional
    public void removeMember(String organizationId, String principalId, String actorId) {
        requireOrganizationExists(organizationId);
        if (!memberRepository.remove(organizationId, principalId)) {
            throw new NotFoundException(ErrorCode.ORGANIZATION_MEMBER_NOT_FOUND,
                    "organization member not found",
                    Map.of("organizationId", organizationId, "principalId", principalId));
        }
        auditPort.record("ORGANIZATION_MEMBER_REMOVED", actorId, organizationId,
                Map.of("principalId", principalId));
    }

    private void requireOrganizationExists(String organizationId) {
        if (!organizationRepository.existsByOrganizationId(organizationId)) {
            throw new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND,
                    "organization not found", Map.of("organizationId", organizationId));
        }
    }

    static void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new ValidationException("code must match " + CODE_PATTERN.pattern());
        }
    }

    static void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new ValidationException("name is required and must be at most 128 characters");
        }
    }
}

/*
 * 功能: ArchUnit 分层与依赖方向门禁——约束 shared、适配层与模块 Mapper 的依赖边界。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * 分层与依赖方向架构门禁。
 *
 * <p>骨架阶段相关包多为占位，规则统一允许 should 集合为空（{@code allowEmptyShould}），
 * 在业务代码落地后立即生效，防止架构腐化。
 */
@AnalyzeClasses(packages = "com.aihub")
class LayeredArchitectureTest {

    private static final String[] BUSINESS_MODULES = {
            "com.aihub.identity..",
            "com.aihub.authorization..",
            "com.aihub.organization..",
            "com.aihub.asset..",
            "com.aihub.taxonomy..",
            "com.aihub.configuration..",
            "com.aihub.version..",
            "com.aihub.transfer..",
            "com.aihub.integration..",
            "com.aihub.platform..",
            "com.aihub.job..",
            "com.aihub.mcp..",
            "com.aihub.audit..",
            "com.aihub.notification..",
            "com.aihub.bootstrap.."
    };

    /** 规则一：shared-kernel 不得依赖任何业务模块或启动层。 */
    @ArchTest
    static final ArchRule shared_kernel_must_not_depend_on_business_modules =
            noClasses().that().resideInAPackage("com.aihub.shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(BUSINESS_MODULES)
                    .allowEmptyShould(true);

    /** 规则二：api 适配层不得直接依赖 infrastructure（Mapper 所在层）。 */
    @ArchTest
    static final ArchRule api_adapters_must_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true);

    /** 规则三：模块 infrastructure 的 Mapper 只能被同模块代码引用。 */
    @ArchTest
    static final ArchRule mappers_must_only_be_used_within_their_module =
            classes().that().haveSimpleNameEndingWith("Mapper")
                    .and().resideInAPackage("..infrastructure..")
                    .should(onlyBeAccessedFromSameModule())
                    .allowEmptyShould(true);

    private static ArchCondition<JavaClass> onlyBeAccessedFromSameModule() {
        return new ArchCondition<>("only be accessed from classes within the same module") {
            @Override
            public void check(JavaClass mapper, ConditionEvents events) {
                String mapperModule = moduleOf(mapper);
                for (Dependency dependency : mapper.getDirectDependenciesToSelf()) {
                    JavaClass origin = dependency.getOriginClass();
                    boolean sameModule = mapperModule.equals(moduleOf(origin));
                    events.add(new SimpleConditionEvent(dependency, sameModule, dependency.getDescription()));
                }
            }
        };
    }

    /**
     * 取 {@code com.aihub} 之后的首个包段作为模块标识；无法识别时返回原包名。
     */
    private static String moduleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        String prefix = "com.aihub.";
        if (!packageName.startsWith(prefix)) {
            return packageName;
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}

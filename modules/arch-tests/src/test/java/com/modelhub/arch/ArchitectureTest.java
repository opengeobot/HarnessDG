package com.modelhub.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构约束（ARCH-001 / 计划阶段1 §6）：
 * 1. Controller 只做编排：不直连 Spring Data 仓库与中间件客户端（Redis/JDBC）；
 * 2. shared 基座不依赖任何业务模块；
 * 3. 业务模块不反向依赖 api/app 装配层；
 * 4. 领域服务不触碰 Servlet API。
 */
@AnalyzeClasses(packages = "com.modelhub", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories_directly =
            noClasses().that().resideInAPackage("com.modelhub.api.controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.modelhub..repo..", "org.springframework.data.repository..")
                    .because("Controller 仅编排应用服务与 Facade（ADR-001 模块化单体分层）");

    @ArchTest
    static final ArchRule controllers_must_not_access_middleware_directly =
            noClasses().that().resideInAPackage("com.modelhub.api.controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.data.redis..", "org.springframework.jdbc..")
                    .because("中间件访问封装在模块服务内部");

    @ArchTest
    static final ArchRule shared_has_no_business_dependencies =
            noClasses().that().resideInAPackage("com.modelhub.shared..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.modelhub.identity..", "com.modelhub.catalog..",
                            "com.modelhub.artifact..", "com.modelhub.interaction..",
                            "com.modelhub.workflow..", "com.modelhub.governance..",
                            "com.modelhub.api..", "com.modelhub.app..")
                    .because("shared 是最底层基座，禁止反向依赖");

    @ArchTest
    static final ArchRule business_modules_do_not_depend_on_assembly_layer =
            noClasses().that().resideInAnyPackage("com.modelhub.identity..", "com.modelhub.shared..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.modelhub.api..", "com.modelhub.app..")
                    .because("业务模块不得反向依赖 REST/装配层");

    @ArchTest
    static final ArchRule domain_services_must_not_touch_servlet =
            noClasses().that().resideInAnyPackage("com.modelhub.identity.service..", "com.modelhub.shared..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("jakarta.servlet..")
                    .because("领域服务与 Servlet 容器解耦");

    @ArchTest
    static final ArchRule repositories_live_in_repo_package =
            classes().that().haveSimpleNameEndingWith("Repository")
                    .and().resideInAPackage("com.modelhub..")
                    .should().resideInAPackage("..repo..")
                    .because("数据访问接口统一存放于 repo 包");
}

/*
 * 功能: Mapper 扫描覆盖门禁——断言 com.aihub 下每个 infrastructure 包中的 *Mapper 接口
 *       都能被启动类声明的 @MapperScan 范围实际扫到，防止更深层模块包静默逃逸 Bean 注册。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
package com.aihub.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.bootstrap.AihubApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ClassUtils;

/**
 * Mapper 扫描覆盖门禁。
 *
 * <p>{@code @MapperScan} 的模式一旦只匹配一级模块包（{@code com.aihub.*.infrastructure}），
 * 更深层的子域包（如 {@code com.aihub.asset.discussion.infrastructure}）中的 Mapper 会**静默**
 * 逃逸 Bean 注册，仓储构造注入失败，可部署 Profile 直接装配失败。该故障只在启动时暴露，
 * 编译与业务单测都无法发现，因此在此固定覆盖不变量。
 *
 * <p>以普通 JUnit Jupiter 测试实现而非 {@code @ArchTest} 字段：仓库内的 ArchUnit JUnit5
 * 引擎当前不产生任何测试用例（{@code tests="0"}），若沿用 {@code @ArchTest}，门禁会静默失效。
 */
class MapperScanCoverageTest {

    @Test
    @DisplayName("每个 ..infrastructure 包中的 Mapper 接口都必须被 @MapperScan 覆盖")
    void everyMapperMustBeReachableByDeclaredMapperScan() {
        Set<String> scanPatterns = declaredScanPatterns();

        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aihub");

        List<String> mappers = mapperClassNames(classes);
        List<String> unreachable = mappers.stream()
                .filter(name -> !isCoveredByScan(name, scanPatterns))
                .sorted()
                .toList();

        assertThat(unreachable)
                .as("以下 Mapper 逃逸了 @MapperScan %s 的覆盖范围，会导致仓储 Bean 装配失败、"
                        + "可部署 Profile 无法启动", scanPatterns)
                .isEmpty();
    }

    @Test
    @DisplayName("门禁本身必须能发现 Mapper：扫描范围非空且含两级模块包")
    void mapperScanMustNotBeVacuous() {
        Set<String> scanPatterns = declaredScanPatterns();
        assertThat(scanPatterns).isNotEmpty();

        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aihub");
        List<String> mappers = mapperClassNames(classes);

        assertThat(mappers)
                .as("classpath 中必须存在 Mapper，否则门禁退化为恒真")
                .isNotEmpty();
        assertThat(mappers.stream().filter(name -> isCoveredByScan(name, scanPatterns)).toList())
                .as("扫描范围必须真实命中全部 Mapper，否则门禁无鉴别力")
                .hasSameSizeAs(mappers);

        // 固定在两级模块包（asset.discussion）上的回归：单星模式曾使该包整体逃逸。
        assertThat(mappers).contains(
                "com.aihub.asset.discussion.infrastructure.DiscussionThreadMapper",
                "com.aihub.asset.discussion.infrastructure.CommentMapper",
                "com.aihub.asset.discussion.infrastructure.CommentRevisionMapper");
    }

    /**
     * 反向不变量：{@code @MapperScan} 必须以 {@code @Mapper} 限定，且 Mapper 接口不得由
     * Spring 托管的适配器类实现。
     *
     * <p>前半段守护扫描收窄：一旦省略 {@code annotationClass}，MyBatis 会把扫描包内**每个**接口都
     * 注册成 Mapper 代理，纯出站端口（曾经的 {@code GiteaRepositoryExistencePort}、
     * {@code GiteaTagVerificationPort}、{@code MinioObjectExistencePort}）就与其 Rest 适配器构成
     * 两个同类型 Bean，{@code ObjectProvider.getIfAvailable()} 抛
     * {@code NoUniqueBeanDefinitionException}，对账任务（ASSET_REPO_RECONCILE、
     * MINIO_STORAGE_RECONCILE）反复重试至 DEAD。
     *
     * <p>后半段堵住同源的镜像退路：若改为给端口接口补 {@code @Mapper} 来满足前半段，Rest 适配器
     * 自身仍会因 {@code @Component} 注册，双 Bean 故障原样复现。故障只在运行期暴露，故在此固定
     * 编译期不变量。
     */
    @Test
    @DisplayName("@MapperScan 必须以 @Mapper 限定，且 Mapper 接口不得由 Spring 托管适配器实现")
    void mapperScanMustBeAnnotationScopedAndMappersMustStayPureProxies() {
        assertThat(declaredMapperScan().annotationClass())
                .as("@MapperScan 必须限定 annotationClass = Mapper.class，"
                        + "否则 infrastructure 包内所有接口都会被注册为 Mapper 代理")
                .isEqualTo(Mapper.class);

        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aihub");

        Set<String> mapperInterfaceNames = classes.stream()
                .filter(javaClass -> javaClass.isInterface())
                .filter(javaClass -> javaClass.isAnnotatedWith(Mapper.class))
                .map(javaClass -> javaClass.getName())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(mapperInterfaceNames).isNotEmpty();

        List<String> conflictingAdapters = classes.stream()
                .filter(javaClass -> !javaClass.isInterface())
                .filter(MapperScanCoverageTest::isSpringManaged)
                .filter(javaClass -> javaClass.getAllRawInterfaces().stream()
                        .anyMatch(itf -> mapperInterfaceNames.contains(itf.getName())))
                .map(javaClass -> javaClass.getName())
                .sorted()
                .toList();

        assertThat(conflictingAdapters)
                .as("以下 Spring 托管 Bean 实现了 @Mapper 接口，会与 MapperScan 代理构成同类型双 Bean；"
                        + "@Mapper 只能标注由 MyBatis 独占生成的纯 Mapper 接口")
                .isEmpty();
    }

    private static boolean isSpringManaged(com.tngtech.archunit.core.domain.JavaClass javaClass) {
        return javaClass.isAnnotatedWith(Component.class)
                || javaClass.isAnnotatedWith(Service.class)
                || javaClass.isAnnotatedWith(Repository.class);
    }

    private static MapperScan declaredMapperScan() {
        MapperScan scan = AihubApplication.class.getAnnotation(MapperScan.class);
        assertThat(scan).as("AihubApplication 必须声明 @MapperScan 才能注册 Mapper").isNotNull();
        return scan;
    }

    private static List<String> mapperClassNames(JavaClasses classes) {
        return classes.stream()
                .filter(javaClass -> javaClass.isInterface())
                .filter(javaClass -> javaClass.getSimpleName().endsWith("Mapper"))
                .filter(javaClass -> javaClass.getPackageName().endsWith(".infrastructure"))
                .map(javaClass -> javaClass.getName())
                .toList();
    }

    /** 读取 {@code @MapperScan} 声明的扫描模式（{@code value} 与 {@code basePackages} 两种写法）。 */
    private static Set<String> declaredScanPatterns() {
        MapperScan scan = declaredMapperScan();
        Set<String> patterns = new LinkedHashSet<>();
        Stream.concat(Arrays.stream(scan.value()), Arrays.stream(scan.basePackages()))
                .filter(pattern -> !pattern.isBlank())
                .forEach(patterns::add);
        assertThat(patterns)
                .as("@MapperScan 必须通过 value 或 basePackages 声明基础包")
                .isNotEmpty();
        return patterns;
    }

    /**
     * 判定某个 Mapper 是否落在扫描范围内。
     *
     * <p>复刻 {@code ClassPathBeanDefinitionScanner.findCandidateComponents} 的判定链：
     * base package 先经 {@link ClassUtils#convertClassNameToResourcePath} 转为资源路径
     * （通配符原样保留），再追加递归 class 通配后缀作为搜索模式，最后以 Spring 自己的
     * {@link AntPathMatcher} 匹配候选类的资源路径。直接复用 Spring 语义，不本地改写通配规则。
     */
    private static boolean isCoveredByScan(String className, Set<String> scanPatterns) {
        AntPathMatcher matcher = new AntPathMatcher();
        String candidateResourcePath = ClassUtils.convertClassNameToResourcePath(className) + ".class";
        for (String scanPattern : scanPatterns) {
            String packageSearchPattern = ClassUtils.convertClassNameToResourcePath(scanPattern) + "/**/*.class";
            if (matcher.match(packageSearchPattern, candidateResourcePath)) {
                return true;
            }
        }
        return false;
    }
}

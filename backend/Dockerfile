# ============================================================================
# 功能: 后端多阶段构建——build 编译 jar；runtime 仅含 JRE 运行控制面；
#       worker 在 runtime 基础上安装锁定版本 git 与 dvc[s3] 承载可靠任务。
# 时间: 2026-06-29
# 作者: AxeXie
# ============================================================================

# ---- 构建阶段：使用带 Maven 的 JDK 21 镜像编译并打包 ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 优先拷贝 wrapper 与 pom 以利用依赖层缓存
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src/ src/
RUN mvn -B -q clean package -DskipTests \
    && cp target/*.jar /workspace/app.jar

# ---- 运行阶段（target: runtime）：仅 JRE，运行控制面 jar ----
FROM eclipse-temurin:21.0.5_11-jre-jammy AS runtime
WORKDIR /app
# 以非 root 运行
RUN groupadd --system aihub && useradd --system --gid aihub --home /app aihub
COPY --from=build /workspace/app.jar /app/app.jar
USER aihub
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---- Worker 阶段（target: worker）：在 runtime 基础上加入 git 与 dvc[s3] ----
FROM runtime AS worker
# 切回 root 安装系统与 Python 依赖
USER root
# 锁定 git 版本（jammy 仓库版本系列），并安装 pip 以装 dvc
RUN sed -i \
        -e 's|http://archive.ubuntu.com/ubuntu/|https://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' \
        -e 's|http://security.ubuntu.com/ubuntu/|https://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' \
        /etc/apt/sources.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        git \
        python3 \
        python3-pip \
    && rm -rf /var/lib/apt/lists/*
# 使用清华 PyPI 镜像（HTTPS）安装锁定版本的 dvc[s3]
RUN python3 -m pip install --no-cache-dir \
        -i https://pypi.tuna.tsinghua.edu.cn/simple \
        "dvc[s3]==3.55.2"
USER aihub
# Worker 以 worker Profile 启动，复用同一 jar 但承载后台任务角色
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=worker"]

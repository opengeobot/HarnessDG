# ModelHub v1 —— 多阶段构建：Maven 构建 modelhub-app 可执行 jar，JRE 运行。
# 构建上下文 = 仓库根目录（compose: deploy/docker-compose.yml 中 context: ..）。
# 注意：已移除可选 syntax directive（# syntax=docker/dockerfile:1），避免内网/受限网
# 络下 Docker 无法拉取 BuildKit frontend 镜像时构建失败。

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 注入阿里云 Maven 镜像（HTTPS），加速国内构建；central 镜像拦截所有仓库请求
RUN mkdir -p /root/.m2 && \
  printf '<?xml version="1.0" encoding="UTF-8"?>\n\
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"\n\
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\n\
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">\n\
  <mirrors>\n\
    <mirror>\n\
      <id>aliyun-public</id>\n\
      <mirrorOf>*</mirrorOf>\n\
      <name>Aliyun Maven Public (HTTPS)</name>\n\
      <url>https://maven.aliyun.com/repository/public</url>\n\
    </mirror>\n\
  </mirrors>\n\
</settings>\n' > /root/.m2/settings.xml

# 先只复制 POM 并预拉依赖：依赖未变时命中 Docker 层缓存，跳过重复下载
COPY pom.xml ./
COPY modules/shared/pom.xml modules/shared/
COPY modules/identity-access/pom.xml modules/identity-access/
COPY modules/catalog/pom.xml modules/catalog/
COPY modules/artifact/pom.xml modules/artifact/
COPY modules/interaction/pom.xml modules/interaction/
COPY modules/workflow/pom.xml modules/workflow/
COPY modules/governance/pom.xml modules/governance/
COPY modules/api/pom.xml modules/api/
COPY modules/app/pom.xml modules/app/
COPY modules/arch-tests/pom.xml modules/arch-tests/
COPY modules/cli/pom.xml modules/cli/
RUN mvn -q -pl modules/app -am dependency:go-offline || true

# 复制源码并打包（跳过测试：容器内无 Docker，Testcontainers 集成测试无法运行）
COPY modules/ modules/
RUN mvn -pl modules/app -am package -Dmaven.test.skip=true

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre
# curl 仅供容器 healthcheck（/actuator/health）使用
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/modules/app/target/modelhub-app-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

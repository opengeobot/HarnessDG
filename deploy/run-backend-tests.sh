#!/bin/sh
# 容器内运行后端测试（宿主机无 JDK 21 时的替代路径）：
#   docker network create modelhub-tcnet   # 宿主侧预建共享测试网络（仅需一次）
#   docker run --rm --network modelhub-tcnet -v <HarnessDG>:/ws -v ~/.m2:/root/.m2 \
#     -v /var/run/docker.sock:/var/run/docker.sock \
#     -w /ws maven:3.9.9-eclipse-temurin-21 sh deploy/run-backend-tests.sh [testFilter]
# 基础设施容器经 modelhub-tcnet 内网容器名直连（绕开宿主发布端口转发 flake），
# 故 JVM 容器必须接入同一网络。
# testFilter 缺省为全量；示例：'OpenApiContractConsistencyTest,OrganizationTests,HealthContractTests'
FILTER="$1"
# testcontainers reuse 仅认用户家目录的 .testcontainers.properties（classpath 无效）：
# 启用后 postgres/redis/gitea/minio 跨 JVM 复用，避开反复启动的端口等待 flake
printf 'testcontainers.reuse.enable=true\n' > "$HOME/.testcontainers.properties"
# 宿主直跑（CI）时自动建网；容器内跑无 docker CLI，由宿主侧预建
command -v docker >/dev/null 2>&1 && docker network create modelhub-tcnet >/dev/null 2>&1 || true
if [ -n "$FILTER" ]; then
  LOG=mvn-keytests.log
  # 宿主机 bind mount 下旧日志可能被 Windows 占用，先删避免重定向 I/O error；
  # clean 强制重编译：bind mount 的 target 可能残留未带 -parameters 的旧类，
  # 增量编译会直接复用导致 @PathVariable 反射解析 500
  rm -f "$LOG" || true
  mvn -pl modules/app -am clean test -Dtest="$FILTER" -Dsurefire.failIfNoSpecifiedTests=false > "$LOG" 2>&1
else
  LOG=mvn-alltests.log
  rm -f "$LOG" || true
  mvn -pl modules/app -am clean test > "$LOG" 2>&1
fi
CODE=$?
tail -n 40 "$LOG" 2>/dev/null || true
echo "mvn-exit=$CODE"
exit $CODE

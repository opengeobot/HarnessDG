#!/bin/sh
# 诊断：从容器内探测新建容器的宿主映射端口可达性
for h in host.docker.internal 192.168.65.254 172.17.0.1; do
  if (echo > /dev/tcp/$h/6390) 2>/dev/null; then
    echo "$h:6390 OK"
  else
    echo "$h:6390 FAIL"
  fi
done
echo "--- 复用的 postgres 端口 65250 对照 ---"
for h in 172.17.0.1 host.docker.internal; do
  if (echo > /dev/tcp/$h/65250) 2>/dev/null; then
    echo "$h:65250 OK"
  else
    echo "$h:65250 FAIL"
  fi
done
echo "--- 默认网关 ---"
ip route

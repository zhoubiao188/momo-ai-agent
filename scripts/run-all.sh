#!/usr/bin/env bash
# 本地一键启动：要求已 mvn package 过，且本机有 JDK 17+。
set -euo pipefail

cd "$(dirname "$0")/.."
JAVA_BIN="${JAVA_BIN:-java}"
LOG_DIR="${LOG_DIR:-/tmp/momo-ai-agent}"

mkdir -p "$LOG_DIR"

echo "==> 启动 biz-service (8082)"
nohup "$JAVA_BIN" -jar biz-service/target/biz-service-1.0.0.jar > "$LOG_DIR/biz.log" 2>&1 &

echo "==> 启动 agent-service (8081)"
nohup "$JAVA_BIN" -jar agent-service/target/agent-service-1.0.0.jar > "$LOG_DIR/agent.log" 2>&1 &

sleep 5

echo "==> 启动 gateway (8080)"
nohup "$JAVA_BIN" -jar gateway/target/gateway-1.0.0.jar > "$LOG_DIR/gw.log" 2>&1 &

echo "等待健康检查..."
for _ in $(seq 1 60); do
  if curl -sf http://localhost:8081/api/agent/health > /dev/null \
     && curl -sf http://localhost:8082/actuator/health > /dev/null \
     && curl -sf http://localhost:8080/actuator/health > /dev/null; then
    echo "全部就绪："
    echo "  网关   http://localhost:8080"
    echo "  Agent  http://localhost:8081"
    echo "  业务   http://localhost:8082"
    echo "日志目录：$LOG_DIR"
    exit 0
  fi
  sleep 2
done

echo "启动超时，请检查 $LOG_DIR 下的日志" >&2
exit 1

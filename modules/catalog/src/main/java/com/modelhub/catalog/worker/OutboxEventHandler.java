package com.modelhub.catalog.worker;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Outbox 事件处理器扩展点（05 §10.1）：跨模块（如 artifact）注册事件处理器，
 * 避免 catalog 反向依赖下游模块。OutboxPoller 按 eventType 路由，
 * 处理器必须幂等（至少一次投递，重复消费不产生副作用，05 §10.2）。
 */
public interface OutboxEventHandler {

    /** 认领的事件类型，全局唯一。 */
    String eventType();

    /** 处理事件；抛异常由 OutboxPoller 统一退避重试。 */
    void handle(JsonNode payload) throws Exception;
}

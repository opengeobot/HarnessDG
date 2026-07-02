/*
 * 功能: 示例幂等任务处理器（no-op），演示 Handler 注册与框架可运行性；真实业务 Handler 属后续阶段。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 示例幂等任务处理器。
 *
 * <p><b>框架示例</b>：以 no-op 方式消费 {@code sample.noop} 类型任务，证明可靠任务框架可运行。
 * 真实业务 Handler（DVC 校验、版本发布、Webhook 消费、对账）属后续阶段，各自实现幂等副作用。
 */
@Component
public class SampleJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SampleJobHandler.class);

    @Override
    public String type() {
        return "sample.noop";
    }

    @Override
    public void handle(JobContext context) {
        LOG.info("sample job handler executed jobId={} type={} attempts={}",
                context.jobId(), context.type(), context.attempts());
    }
}

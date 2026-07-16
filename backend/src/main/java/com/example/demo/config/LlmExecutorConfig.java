package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * LLM 改进建议生成的专用线程池。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>独立于 Tomcat worker 线程池，避免长任务占用 HTTP 处理线程</li>
 *   <li>有界队列 + 调用方运行策略：满负载时优先让 HTTP 线程执行兜底任务，丢弃新提交</li>
 *   <li>线程名前缀 {@code llm-} 便于日志/线程栈定位</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableAsync
public class LlmExecutorConfig {

    @Bean(name = "llmExecutor")
    public Executor llmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("llm-");
        // 队列满时由调用线程（即 HTTP 请求线程）自己跑，避免静默丢弃分析任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[启动诊断] LLM 异步线程池 llmExecutor 初始化完成：core=2, max=4, queue=16");
        return executor;
    }
}
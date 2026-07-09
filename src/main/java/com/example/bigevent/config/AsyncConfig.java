package com.example.bigevent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 * 用于 AI 调用、文件解析等 IO 密集型任务，避免阻塞 Tomcat 主线程
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 通用异步任务线程池
     * 适用于：AI 调用、文件解析、消息推送等
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：CPU 核心数
        executor.setCorePoolSize(4);
        // 最大线程数：核心数 * 2 + IO 缓冲
        executor.setMaxPoolSize(20);
        // 队列容量：缓冲等待执行的任务
        executor.setQueueCapacity(200);
        // 线程名前缀，方便日志排查
        executor.setThreadNamePrefix("bigevent-async-");
        // 拒绝策略：队列满时，由调用线程自己执行（保证不丢任务，只是慢一点）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务完成后再关闭容器
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 优雅关闭等待时间
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * AI 专用线程池（IO 密集型，线程数可以多一些）
     * 适用于：调用外部大模型 API
     */
    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // AI 调用是 IO 密集型（等网络响应），线程数可以大于 CPU 核心数
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("bigevent-ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}

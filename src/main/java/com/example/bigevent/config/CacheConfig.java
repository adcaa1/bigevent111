package com.example.bigevent.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存配置类
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 配置基于内存的缓存管理器
     * ConcurrentMapCacheManager 会自动创建不存在的缓存区域
     * 
     * @return CacheManager
     */
    @Bean
    public CacheManager cacheManager() {
        // 使用 ConcurrentHashMap 作为底层存储，适合开发和测试环境
        return new ConcurrentMapCacheManager();
    }
}
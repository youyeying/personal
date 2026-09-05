package com.personal.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 字典缓存配置（Caffeine 本地缓存）
 * - 覆盖：食物 / 锻炼动作 / 记账分类等几乎不变、但每次进页都全量拉的字典
 * - 策略：写后 5 分钟过期 + 写方法 @CacheEvict 立即失效（双保险，最多脏 5 分钟，自用可接受）
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("foodItems", "exerciseItems", "expenseCategories");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(64));
        return manager;
    }
}

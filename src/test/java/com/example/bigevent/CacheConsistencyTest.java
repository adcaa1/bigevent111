package com.example.bigevent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存双写一致性测试演示
 *
 * 用 ConcurrentHashMap 模拟数据库
 * 用 Redis 模拟缓存
 * 通过打印日志观察两种方案的执行时序
 */
@SpringBootTest
public class CacheConsistencyTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // ========== 模拟数据库 ==========
    private static final ConcurrentHashMap<Integer, String> mockDB = new ConcurrentHashMap<>();

    // 缓存 key 前缀
    private static final String USER_CACHE_KEY = "user:";

    // ========== 工具：模拟"查询用户"（带缓存逻辑） ==========
    public String getUserFromCache(Integer userId) {
        // 1. 先查缓存
        String cacheValue = redisTemplate.opsForValue().get(USER_CACHE_KEY + userId);
        if (cacheValue != null) {
            System.out.println("  [查询] 缓存命中，值=" + cacheValue);
            return cacheValue;
        }

        // 2. 缓存未命中，查"数据库"
        String dbValue = mockDB.get(userId);
        System.out.println("  [查询] 缓存未命中，查数据库，值=" + dbValue);

        // 3. 回写缓存（设置10分钟过期，实际项目中要加过期时间）
        if (dbValue != null) {
            redisTemplate.opsForValue().set(USER_CACHE_KEY + userId, dbValue, Duration.ofMinutes(10));
            System.out.println("  [查询] 数据库值已回写到缓存");
        }
        return dbValue;
    }

    // ========== 方案二：先更新数据库，再删缓存（Cache Aside） ==========
    public void updateUser_CacheAside(Integer userId, String newName) {
        System.out.println("\n===== 方案二：先更新数据库，再删缓存 =====");

        // Step 1：更新数据库
        mockDB.put(userId, newName);
        System.out.println("  Step 1: 数据库更新成功 → " + newName);

        // Step 2：删除缓存（注意是删除，不是更新）
        redisTemplate.delete(USER_CACHE_KEY + userId);
        System.out.println("  Step 2: 缓存已删除");
    }

    // ========== 方案四：延迟双删 ==========
    public void updateUser_DelayedDoubleDelete(Integer userId, String newName) {
        System.out.println("\n===== 方案四：延迟双删 =====");

        // Step 1：先删一次缓存（先把旧值清掉）
        redisTemplate.delete(USER_CACHE_KEY + userId);
        System.out.println("  Step 1: 第一次删缓存");

        // Step 2：更新数据库
        mockDB.put(userId, newName);
        System.out.println("  Step 2: 数据库更新成功 → " + newName);

        // Step 3：睡眠一段时间（模拟延迟，实际项目中根据业务耗时调整）
        System.out.println("  Step 3: 等待 500ms...（等可能读到旧值的线程把值写回缓存）");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Step 4：再删一次缓存（把期间可能写进去的旧值清掉）
        redisTemplate.delete(USER_CACHE_KEY + userId);
        System.out.println("  Step 4: 延迟 500ms 后第二次删缓存");
    }

    // ========== 测试：方案二演示 ==========
    @Test
    public void testCacheAside() {
        Integer userId = 1;

        // 准备数据：先往"数据库"和缓存里放一个初始值
        mockDB.put(userId, "张三");
        redisTemplate.opsForValue().set(USER_CACHE_KEY + userId, "张三", Duration.ofMinutes(10));
        System.out.println("\n【准备数据】数据库和缓存都是：张三");

        // 第一次查询（命中缓存）
        getUserFromCache(userId);

        // 执行方案二更新
        updateUser_CacheAside(userId, "张三-已修改");

        // 再次查询（缓存已被删，会走数据库）
        getUserFromCache(userId);

        // 清理
        redisTemplate.delete(USER_CACHE_KEY + userId);
        mockDB.remove(userId);
    }

    // ========== 测试：方案四演示 ==========
    @Test
    public void testDelayedDoubleDelete() {
        Integer userId = 2;

        // 准备数据
        mockDB.put(userId, "李四");
        redisTemplate.opsForValue().set(USER_CACHE_KEY + userId, "李四", Duration.ofMinutes(10));
        System.out.println("\n【准备数据】数据库和缓存都是：李四");

        // 第一次查询（命中缓存）
        getUserFromCache(userId);

        // 执行方案四更新
        updateUser_DelayedDoubleDelete(userId, "李四-已修改");

        // 再次查询（第一次删缓存后如果其他线程读了旧值，第二次删会把它清掉）
        getUserFromCache(userId);

        // 清理
        redisTemplate.delete(USER_CACHE_KEY + userId);
        mockDB.remove(userId);
    }

    // ========== 测试：模拟并发下的方案二（极端情况说明） ==========
    @Test
    public void testCacheAsideConcurrentRisk() throws InterruptedException {
        System.out.println("\n===== 方案二并发风险说明（理论演示） =====");
        System.out.println("  这种时序极难发生，但理论上有风险：");
        System.out.println("  线程A：读缓存未命中 → 读数据库（拿到旧值） → [卡顿/网络延迟]");
        System.out.println("  线程B：更新数据库 → 删缓存");
        System.out.println("  线程A：[恢复] → 把旧值写入缓存  ← 此时缓存是脏数据");
        System.out.println("  ");
        System.out.println("  为什么极难发生？因为线程A的'读DB+写Cache'通常比线程B的'写DB'更快。");
        System.out.println("  万一真发生了怎么办？给缓存加过期时间（比如10分钟），过期后自动失效。");
    }
}

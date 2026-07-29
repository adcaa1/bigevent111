package com.example.bigevent.job;

import com.example.bigevent.mapper.Usermapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 用户计数校准定时任务
 * <p>
 * 每天凌晨 3 点全量校准一次 fans_count / follow_count / article_count，
 * 修复因并发、异常或历史数据迁移导致的冗余计数漂移。
 */
@Component
public class UserCountSyncJob {

    private static final Logger log = LoggerFactory.getLogger(UserCountSyncJob.class);

    @Autowired
    private Usermapper usermapper;

    /**
     * 每天凌晨 03:00 执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void syncUserCounts() {
        log.info("开始执行用户计数全量校准任务");
        try {
            int rows = usermapper.syncAllUserCounts();
            log.info("用户计数全量校准完成，共校准 {} 位用户", rows);
        } catch (Exception e) {
            log.error("用户计数全量校准失败", e);
        }
    }
}

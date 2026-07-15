package com.example.bigevent.service;

import com.example.bigevent.domain.UserFact;
import com.example.bigevent.mapper.UserFactMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户关键事实服务（长期业务记忆）
 * <p>
 * 把用户的稳定信息（如昵称、最近添加的文章等）持久化到 MySQL，
 * 在 AI 对话时作为系统上下文注入，避免换会话后业务信息丢失。
 */
@Service
public class UserFactService {

    @Autowired
    private UserFactMapper userFactMapper;

    /**
     * 查询某个用户的所有事实。
     *
     * @param userId 用户 ID
     * @return 用户事实列表
     */
    public List<UserFact> getFactsByUserId(Integer userId) {
        return userFactMapper.listByUserId(userId);
    }

    /**
     * 查询某个用户在指定事实键下的值。
     *
     * @param userId  用户 ID
     * @param factKey 事实键，例如 "nickname"、"latest_articles"
     * @return 事实值，不存在时返回 null
     */
    public String getFactValue(Integer userId, String factKey) {
        UserFact fact = userFactMapper.findByUserIdAndKey(userId, factKey);
        return fact == null ? null : fact.getFactValue();
    }

    /**
     * 保存或更新一条用户事实。
     * <p>
     * 数据库使用 (user_id, fact_key) 唯一索引，重复写入时执行覆盖更新。
     *
     * @param userId    用户 ID
     * @param factKey   事实键
     * @param factValue 事实值
     */
    public void saveOrUpdateFact(Integer userId, String factKey, String factValue) {
        UserFact fact = new UserFact();
        fact.setUserId(userId);
        fact.setFactKey(factKey);
        fact.setFactValue(factValue);
        userFactMapper.saveOrUpdate(fact);
    }

    /**
     * 把用户的所有事实格式化为可直接插入 Prompt 的文本块。
     * <p>
     * 如果用户没有任何事实，返回空字符串，避免在 Prompt 中生成无意义章节。
     *
     * @param userId 用户 ID
     * @return 格式化后的用户关键信息文本
     */
    public String formatFactsForPrompt(Integer userId) {
        List<UserFact> facts = getFactsByUserId(userId);
        if (facts == null || facts.isEmpty()) {
            return "";
        }
        return facts.stream()
                .map(f -> "- " + f.getFactKey() + "：" + f.getFactValue())
                .collect(Collectors.joining("\n", "用户关键信息：\n", ""));
    }

    /**
     * 删除某个用户的全部事实（用于注销账号等场景）。
     *
     * @param userId 用户 ID
     */
    public void deleteByUserId(Integer userId) {
        userFactMapper.deleteByUserId(userId);
    }
}

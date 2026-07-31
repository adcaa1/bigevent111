package com.example.bigevent.util;

/**
 * 聊天会话 ID 生成工具。
 * <p>
 * conversation_id 设计规则：
 * <ul>
 *     <li>单聊：private:{较小用户ID}:{较大用户ID}，保证 A 和 B 聊天时两边生成的 ID 一致</li>
 *     <li>群聊：group:{群ID}</li>
 * </ul>
 * 使用该 ID 可以直接命中 chat_message.conversation_id 索引，避免单聊 OR/UNION ALL 查询。
 */
public class ChatConversationUtil {

    private static final String PRIVATE_PREFIX = "private";
    private static final String GROUP_PREFIX = "group";

    /**
     * 生成单聊会话 ID
     */
    public static String privateConversationId(Integer userId1, Integer userId2) {
        if (userId1 == null || userId2 == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        int small = Math.min(userId1, userId2);
        int large = Math.max(userId1, userId2);
        return PRIVATE_PREFIX + ":" + small + ":" + large;
    }

    /**
     * 生成群聊会话 ID
     */
    public static String groupConversationId(Integer groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("群ID不能为空");
        }
        return GROUP_PREFIX + ":" + groupId;
    }
}

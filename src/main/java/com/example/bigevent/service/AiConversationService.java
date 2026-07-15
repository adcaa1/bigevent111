package com.example.bigevent.service;

import com.example.bigevent.domain.AiConversation;
import com.example.bigevent.mapper.AiConversationMapper;
import com.example.bigevent.repository.RedisChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 会话管理服务
 * <p>
 * 负责会话的增删改查，并在删除会话时级联清理短期记忆（Redis）和长期语义记忆（ES）。
 */
@Service
public class AiConversationService {

    @Autowired
    private AiConversationMapper aiConversationMapper;

    @Autowired
    private RedisChatMemoryStore redisChatMemoryStore;

    @Autowired
    private ChatHistoryVectorService chatHistoryVectorService;

    /**
     * 创建一个新的 AI 会话。
     *
     * @param userId 会话所属用户 ID
     * @param title  会话标题，为空时默认"新对话"
     * @return 创建成功的会话对象
     */
    public AiConversation createConversation(Integer userId, String title) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(title == null || title.isBlank() ? "新对话" : title);
        aiConversationMapper.insert(conversation);
        return conversation;
    }

    /**
     * 查询某用户的所有会话，按最近更新时间倒序排列。
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    public List<AiConversation> listByUserId(Integer userId) {
        return aiConversationMapper.listByUserId(userId);
    }

    /**
     * 根据 ID 查询单个会话。
     *
     * @param id 会话 ID
     * @return 会话对象，不存在时返回 null
     */
    public AiConversation findById(Long id) {
        return aiConversationMapper.findById(id);
    }

    /**
     * 修改会话标题。
     *
     * @param id    会话 ID
     * @param title 新标题
     */
    public void updateTitle(Long id, String title) {
        aiConversationMapper.updateTitle(id, title);
    }

    /**
     * 删除指定会话，并级联清理相关记忆数据。
     * <p>
     * 级联清理范围：
     * <ul>
     *     <li>Redis 中该会话的短期记忆（chat:memory:{conversationId}）</li>
     *     <li>Elasticsearch 中该会话产生的长期语义向量</li>
     * </ul>
     *
     * @param id 会话 ID
     */
    public void deleteConversation(Long id) {
        AiConversation conversation = aiConversationMapper.findById(id);
        if (conversation == null) {
            return;
        }
        // 级联清理 Redis 短期记忆
        redisChatMemoryStore.deleteMessages(String.valueOf(id));
        // 级联清理 ES 长期语义记忆
        chatHistoryVectorService.deleteByConversationId(String.valueOf(id));
        // 删除会话记录
        aiConversationMapper.deleteById(id);
    }
}

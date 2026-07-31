package com.example.bigevent.service.Impl;

import com.example.bigevent.domain.ChatGroup;
import com.example.bigevent.domain.ChatGroupMember;
import com.example.bigevent.domain.ChatMessage;
import com.example.bigevent.domain.User;
import com.example.bigevent.domain.vo.ChatGroupMemberVO;
import com.example.bigevent.domain.vo.ChatGroupVO;
import com.example.bigevent.domain.vo.ChatMessageVO;
import com.example.bigevent.domain.vo.ConversationVO;
import com.example.bigevent.mapper.ChatGroupMapper;
import com.example.bigevent.mapper.ChatMessageMapper;
import com.example.bigevent.mapper.Usermapper;
import com.example.bigevent.service.ChatService;
import com.example.bigevent.util.ChatConversationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天服务实现类
 */
@Service
public class ChatServiceImpl implements ChatService {

    /**
     * 单个群最大成员数（含群主），防止万人群拖垮推送与数据库。
     * 后续可抽到配置文件。
     */
    private static final int MAX_GROUP_MEMBERS = 100;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatGroupMapper chatGroupMapper;

    @Autowired
    private Usermapper usermapper;

    @Override
    @Transactional
    public ChatMessage savePrivateMessage(Integer senderId, Integer receiverId, String content, String tempId) {
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setConversationId(ChatConversationUtil.privateConversationId(senderId, receiverId));
        message.setContent(content);
        message.setType(0);
        message.setIsRead(0);
        message.setTempId(tempId);
        message.setCreateTime(java.time.LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }

    @Override
    @Transactional
    public ChatMessage saveGroupMessage(Integer senderId, Integer groupId, String content, String tempId) {
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setGroupId(groupId);
        message.setConversationId(ChatConversationUtil.groupConversationId(groupId));
        message.setContent(content);
        message.setType(1);
        message.setIsRead(0);
        message.setTempId(tempId);
        message.setCreateTime(java.time.LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }

    @Override
    public List<ChatMessageVO> getPrivateHistory(Integer userId, Integer friendId) {
        String conversationId = ChatConversationUtil.privateConversationId(userId, friendId);
        List<ChatMessage> messages = chatMessageMapper.findPrivateMessages(conversationId);
        List<ChatMessageVO> voList = new ArrayList<>();
        for (ChatMessage msg : messages) {
            voList.add(convertToVO(msg));
        }
        return voList;
    }

    @Override
    public List<ChatMessageVO> getPrivateHistoryPage(Integer userId, Integer friendId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String conversationId = ChatConversationUtil.privateConversationId(userId, friendId);
        // 查出来的结果是 DESC（最新的在前面），需要反转成 ASC（最旧的在前面）
        List<ChatMessage> messages = chatMessageMapper.findPrivateMessagesPage(conversationId, offset, pageSize);
        List<ChatMessageVO> voList = new ArrayList<>();
        for (ChatMessage msg : messages) {
            voList.add(convertToVO(msg));
        }
        // 反转，使最新的在最后面，这样前端直接 v-for 显示就是从上到下时间递增
        java.util.Collections.reverse(voList);
        return voList;
    }

    @Override
    public long countPrivateMessages(Integer userId, Integer friendId) {
        String conversationId = ChatConversationUtil.privateConversationId(userId, friendId);
        return chatMessageMapper.countPrivateMessages(conversationId);
    }

    @Override
    public List<ChatMessageVO> getGroupHistory(Integer groupId) {
        String conversationId = ChatConversationUtil.groupConversationId(groupId);
        List<ChatMessage> messages = chatMessageMapper.findGroupMessages(conversationId);
        List<ChatMessageVO> voList = new ArrayList<>();
        for (ChatMessage msg : messages) {
            voList.add(convertToVO(msg));
        }
        return voList;
    }

    @Override
    public List<ChatMessageVO> getGroupHistoryPage(Integer groupId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String conversationId = ChatConversationUtil.groupConversationId(groupId);
        List<ChatMessage> messages = chatMessageMapper.findGroupMessagesPage(conversationId, offset, pageSize);
        List<ChatMessageVO> voList = new ArrayList<>();
        for (ChatMessage msg : messages) {
            voList.add(convertToVO(msg));
        }
        java.util.Collections.reverse(voList);
        return voList;
    }

    @Override
    public long countGroupMessages(Integer groupId) {
        String conversationId = ChatConversationUtil.groupConversationId(groupId);
        return chatMessageMapper.countGroupMessages(conversationId);
    }

    @Override
    public List<ConversationVO> getConversations(Integer userId) {
        return chatMessageMapper.findConversations(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Integer userId, Integer friendId) {
        chatMessageMapper.markAsRead(userId, friendId);
    }

    @Override
    public long countUnread(Integer userId, Integer friendId) {
        return chatMessageMapper.countUnread(userId, friendId);
    }

    @Override
    public long countTotalUnread(Integer userId) {
        return chatMessageMapper.countTotalUnread(userId);
    }

    @Override
    @Transactional
    public ChatGroup createGroup(String name, Integer creatorId, List<Integer> memberIds) {
        // 含创建者计算总人数
        int total = 1 + (memberIds == null ? 0 : (int) memberIds.stream().distinct().count());
        if (total > MAX_GROUP_MEMBERS) {
            throw new RuntimeException("群成员数量超过上限 " + MAX_GROUP_MEMBERS);
        }

        ChatGroup group = new ChatGroup();
        group.setName(name);
        group.setCreatorId(creatorId);
        chatGroupMapper.insertGroup(group);

        // 创建者作为群主
        ChatGroupMember creator = new ChatGroupMember();
        creator.setGroupId(group.getId());
        creator.setUserId(creatorId);
        creator.setRole(2);
        chatGroupMapper.insertMember(creator);

        // 添加其他成员，去重并排除创建者
        if (memberIds != null) {
            memberIds.stream()
                    .distinct()
                    .filter(userId -> userId != null && !userId.equals(creatorId))
                    .forEach(userId -> {
                        ChatGroupMember member = new ChatGroupMember();
                        member.setGroupId(group.getId());
                        member.setUserId(userId);
                        member.setRole(0);
                        chatGroupMapper.insertMember(member);
                    });
        }
        return group;
    }

    @Override
    @Transactional
    public void addGroupMember(Integer groupId, Integer userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        int currentCount = chatGroupMapper.countMembers(groupId);
        if (currentCount >= MAX_GROUP_MEMBERS) {
            throw new RuntimeException("群成员数量已达上限 " + MAX_GROUP_MEMBERS);
        }
        if (chatGroupMapper.isMember(groupId, userId) > 0) {
            throw new RuntimeException("该用户已在群中");
        }
        ChatGroupMember member = new ChatGroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(0); // 邀请入群默认普通成员，防止误提权
        chatGroupMapper.insertMember(member);
    }

    @Override
    @Transactional
    public void setGroupMemberRole(Integer groupId, Integer userId, Integer role, Integer operatorId) {
        if (userId == null || role == null) {
            throw new RuntimeException("用户ID和角色不能为空");
        }
        if (role < 0 || role > 1) {
            throw new RuntimeException("只能设置为普通成员(0)或群管理员(1)");
        }
        // 只有群主能设置管理员
        Integer operatorRole = chatGroupMapper.findUserRole(groupId, operatorId);
        if (operatorRole == null || operatorRole != 2) {
            throw new RuntimeException("只有群主可以设置管理员");
        }
        // 不能修改群主自己的角色
        Integer targetRole = chatGroupMapper.findUserRole(groupId, userId);
        if (targetRole == null) {
            throw new RuntimeException("该用户不在群中");
        }
        if (targetRole == 2) {
            throw new RuntimeException("不能修改群主的角色");
        }
        chatGroupMapper.updateMemberRole(groupId, userId, role);
    }

    @Override
    @Transactional
    public void removeGroupMember(Integer groupId, Integer userId, Integer operatorId) {
        // 检查操作者权限：群主或管理员
        Integer operatorRole = chatGroupMapper.findUserRole(groupId, operatorId);
        if (operatorRole == null || operatorRole < 1) {
            throw new RuntimeException("没有权限移出成员");
        }
        // 不能移出群主
        Integer targetRole = chatGroupMapper.findUserRole(groupId, userId);
        if (targetRole != null && targetRole == 2) {
            throw new RuntimeException("不能移出群主");
        }
        chatGroupMapper.deleteMember(groupId, userId);
    }

    @Override
    @Transactional
    public void quitGroup(Integer groupId, Integer userId) {
        // 群主不能主动退群，必须先转让或解散
        Integer role = chatGroupMapper.findUserRole(groupId, userId);
        if (role == null) {
            throw new RuntimeException("您不在该群中");
        }
        if (role == 2) {
            throw new RuntimeException("群主请先转让群或解散群");
        }
        chatGroupMapper.deleteMember(groupId, userId);
    }

    @Override
    @Transactional
    public void dissolveGroup(Integer groupId, Integer operatorId) {
        Integer role = chatGroupMapper.findUserRole(groupId, operatorId);
        if (role == null || role != 2) {
            throw new RuntimeException("只有群主可以解散群聊");
        }
        chatGroupMapper.deleteAllMembers(groupId);
        chatGroupMapper.deleteGroup(groupId);
    }

    @Override
    @Transactional
    public void updateGroup(Integer groupId, String name, String avatar, Integer operatorId) {
        Integer role = chatGroupMapper.findUserRole(groupId, operatorId);
        if (role == null || role < 1) {
            throw new RuntimeException("没有权限修改群信息");
        }
        ChatGroup group = chatGroupMapper.findById(groupId);
        if (group == null) {
            throw new RuntimeException("群不存在");
        }
        if (name != null && !name.trim().isEmpty()) {
            group.setName(name.trim());
        }
        if (avatar != null) {
            group.setAvatar(avatar);
        }
        chatGroupMapper.updateGroup(group);
    }

    @Override
    public List<ChatGroupVO> getUserGroups(Integer userId) {
        // 一次性查询群列表和成员数，避免 N+1
        return chatGroupMapper.findGroupVOsByUserId(userId);
    }

    @Override
    public List<ChatGroupMemberVO> getGroupMembers(Integer groupId) {
        List<ChatGroupMember> members = chatGroupMapper.findMembersByGroupId(groupId);
        List<ChatGroupMemberVO> voList = new ArrayList<>();
        for (ChatGroupMember member : members) {
            ChatGroupMemberVO vo = new ChatGroupMemberVO();
            vo.setId(member.getId());
            vo.setGroupId(member.getGroupId());
            vo.setUserId(member.getUserId());
            vo.setRole(member.getRole());
            vo.setJoinTime(member.getJoinTime());

            User user = usermapper.findById(member.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
                vo.setUserPic(user.getUserPic());
            }
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public boolean isGroupMember(Integer groupId, Integer userId) {
        return chatGroupMapper.isMemberFast(groupId, userId) != null;
    }

    @Override
    public ChatGroup getGroupById(Integer groupId) {
        return chatGroupMapper.findById(groupId);
    }

    @Override
    public ChatMessage getMessageBySenderAndTempId(Integer senderId, String tempId) {
        return chatMessageMapper.findBySenderAndTempId(senderId, tempId);
    }

    /**
     * 转换为消息VO
     */
    private ChatMessageVO convertToVO(ChatMessage msg) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(msg.getId());
        vo.setSenderId(msg.getSenderId());
        vo.setReceiverId(msg.getReceiverId());
        vo.setGroupId(msg.getGroupId());
        vo.setConversationId(msg.getConversationId());
        vo.setContent(msg.getContent());
        vo.setType(msg.getType());
        vo.setIsRead(msg.getIsRead());
        vo.setCreateTime(msg.getCreateTime());

        // 填充发送者信息
        User sender = usermapper.findById(msg.getSenderId());
        if (sender != null) {
            if (sender.getDeleted() != null && sender.getDeleted() == 1) {
                vo.setSenderNickname("已注销用户");
            } else {
                vo.setSenderNickname(sender.getNickname());
            }
            vo.setSenderAvatar(sender.getUserPic());
        }

        // 填充群名称
        if (msg.getGroupId() != null) {
            ChatGroup group = chatGroupMapper.findById(msg.getGroupId());
            if (group != null) {
                vo.setGroupName(group.getName());
            }
        }

        return vo;
    }
}

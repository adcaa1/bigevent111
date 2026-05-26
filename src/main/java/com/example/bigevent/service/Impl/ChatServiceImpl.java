package com.example.bigevent.service.Impl;

import com.example.bigevent.domain.ChatGroup;
import com.example.bigevent.domain.ChatGroupMember;
import com.example.bigevent.domain.ChatMessage;
import com.example.bigevent.domain.User;
import com.example.bigevent.domain.vo.ChatGroupMemberVO;
import com.example.bigevent.domain.vo.ChatGroupVO;
import com.example.bigevent.domain.vo.ChatMessageVO;
import com.example.bigevent.mapper.ChatGroupMapper;
import com.example.bigevent.mapper.ChatMessageMapper;
import com.example.bigevent.mapper.Usermapper;
import com.example.bigevent.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天服务实现类
 */
@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatGroupMapper chatGroupMapper;

    @Autowired
    private Usermapper usermapper;

    @Override
    @Transactional
    public ChatMessage savePrivateMessage(Integer senderId, Integer receiverId, String content) {
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setType(0);
        message.setIsRead(0);
        chatMessageMapper.insert(message);
        return message;
    }

    @Override
    @Transactional
    public ChatMessage saveGroupMessage(Integer senderId, Integer groupId, String content) {
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setGroupId(groupId);
        message.setContent(content);
        message.setType(1);
        message.setIsRead(0);
        chatMessageMapper.insert(message);
        return message;
    }

    @Override
    public List<ChatMessageVO> getPrivateHistory(Integer userId, Integer friendId) {
        List<ChatMessage> messages = chatMessageMapper.findPrivateMessages(userId, friendId);
        List<ChatMessageVO> voList = new ArrayList<>();
        for (ChatMessage msg : messages) {
            voList.add(convertToVO(msg));
        }
        return voList;
    }

    @Override
    public List<ChatMessageVO> getGroupHistory(Integer groupId) {
        List<ChatMessage> messages = chatMessageMapper.findGroupMessages(groupId);
        List<ChatMessageVO> voList = new ArrayList<>();
        for (ChatMessage msg : messages) {
            voList.add(convertToVO(msg));
        }
        return voList;
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

        // 添加其他成员
        if (memberIds != null) {
            for (Integer userId : memberIds) {
                if (userId.equals(creatorId)) {
                    continue;
                }
                ChatGroupMember member = new ChatGroupMember();
                member.setGroupId(group.getId());
                member.setUserId(userId);
                member.setRole(0);
                chatGroupMapper.insertMember(member);
            }
        }
        return group;
    }

    @Override
    @Transactional
    public void addGroupMember(Integer groupId, Integer userId, Integer role) {
        if (chatGroupMapper.isMember(groupId, userId) > 0) {
            throw new RuntimeException("该用户已在群中");
        }
        ChatGroupMember member = new ChatGroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(role != null ? role : 0);
        chatGroupMapper.insertMember(member);
    }

    @Override
    @Transactional
    public void removeGroupMember(Integer groupId, Integer userId, Integer operatorId) {
        // 检查操作者权限
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
    public List<ChatGroupVO> getUserGroups(Integer userId) {
        List<ChatGroup> groups = chatGroupMapper.findGroupsByUserId(userId);
        List<ChatGroupVO> voList = new ArrayList<>();
        for (ChatGroup group : groups) {
            ChatGroupVO vo = new ChatGroupVO();
            vo.setId(group.getId());
            vo.setName(group.getName());
            vo.setCreatorId(group.getCreatorId());
            vo.setAvatar(group.getAvatar());
            vo.setCreateTime(group.getCreateTime());
            vo.setMemberCount(chatGroupMapper.findMembersByGroupId(group.getId()).size());
            voList.add(vo);
        }
        return voList;
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
        return chatGroupMapper.isMember(groupId, userId) > 0;
    }

    @Override
    public ChatGroup getGroupById(Integer groupId) {
        return chatGroupMapper.findById(groupId);
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
        vo.setContent(msg.getContent());
        vo.setType(msg.getType());
        vo.setIsRead(msg.getIsRead());
        vo.setCreateTime(msg.getCreateTime());

        // 填充发送者信息
        User sender = usermapper.findById(msg.getSenderId());
        if (sender != null) {
            vo.setSenderNickname(sender.getNickname());
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

package com.example.bigevent.controller;

import com.example.bigevent.domain.ChatGroup;
import com.example.bigevent.domain.Result;
import com.example.bigevent.domain.vo.ChatGroupMemberVO;
import com.example.bigevent.domain.vo.ChatGroupVO;
import com.example.bigevent.domain.vo.ChatMessageVO;
import com.example.bigevent.domain.vo.ConversationVO;
import com.example.bigevent.service.ChatService;
import com.example.bigevent.util.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 聊天消息 Controller
 * 提供历史消息查询、已读标记、群聊管理等 REST 接口
 */
@RestController
@RequestMapping("/chat")
public class ChatMessageController {

    @Autowired
    private ChatService chatService;

    /**
     * 获取与某用户的聊天记录（分页）
     */
    @GetMapping("/history/{userId}")
    public Result<List<ChatMessageVO>> getPrivateHistory(@PathVariable Integer userId,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<ChatMessageVO> list = chatService.getPrivateHistoryPage(currentUserId, userId, page, pageSize);
        return Result.success(list);
    }

    /**
     * 获取群聊历史记录（分页）
     */
    @GetMapping("/history/group/{groupId}")
    public Result<List<ChatMessageVO>> getGroupHistory(@PathVariable Integer groupId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");

        // 检查用户是否在群中
        if (!chatService.isGroupMember(groupId, currentUserId)) {
            return Result.error("您不在该群中");
        }
        List<ChatMessageVO> list = chatService.getGroupHistoryPage(groupId, page, pageSize);
        return Result.success(list);
    }

    /**
     * 将与某用户的所有消息标记为已读
     */
    @PutMapping("/read/{userId}")
    public Result markAsRead(@PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        chatService.markAsRead(currentUserId, userId);
        return Result.success();
    }

    /**
     * 获取总未读消息数
     */
    @GetMapping("/unread/count")
    public Result<Long> getTotalUnread() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        long count = chatService.countTotalUnread(currentUserId);
        return Result.success(count);
    }

    /**
     * 获取某用户发来的未读消息数
     */
    @GetMapping("/unread/{userId}")
    public Result<Long> getUnreadFromUser(@PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        long count = chatService.countUnread(currentUserId, userId);
        return Result.success(count);
    }

    /**
     * 获取会话列表（每个对话对方的最新消息 + 未读数）
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> getConversations() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<ConversationVO> list = chatService.getConversations(currentUserId);
        return Result.success(list);
    }

    // ==================== 群聊管理 ====================

    /**
     * 获取我加入的群列表
     */
    @GetMapping("/groups")
    public Result<List<ChatGroupVO>> getMyGroups() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<ChatGroupVO> list = chatService.getUserGroups(currentUserId);
        return Result.success(list);
    }

    /**
     * 创建群聊
     */
    @PostMapping("/group")
    public Result<ChatGroup> createGroup(@RequestParam String name,
                                         @RequestParam(required = false) List<Integer> memberIds) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        if (name == null || name.trim().isEmpty()) {
            return Result.error("群名称不能为空");
        }
        ChatGroup group = chatService.createGroup(name.trim(), currentUserId, memberIds);
        return Result.success(group);
    }

    /**
     * 邀请用户入群
     */
    @PostMapping("/group/{groupId}/member/{userId}")
    public Result addGroupMember(@PathVariable Integer groupId, @PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");

        ChatGroup group = chatService.getGroupById(groupId);
        if (group == null) {
            return Result.error("群不存在");
        }

        // 仅群主和管理员可以邀请
        // 这里简化：任何人都可以邀请（后续可按需加权限）
        chatService.addGroupMember(groupId, userId, 0);
        return Result.success();
    }

    /**
     * 移出群成员
     */
    @DeleteMapping("/group/{groupId}/member/{userId}")
    public Result removeGroupMember(@PathVariable Integer groupId, @PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");

        try {
            chatService.removeGroupMember(groupId, userId, currentUserId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取群成员列表
     */
    @GetMapping("/group/{groupId}/members")
    public Result<List<ChatGroupMemberVO>> getGroupMembers(@PathVariable Integer groupId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");

        if (!chatService.isGroupMember(groupId, currentUserId)) {
            return Result.error("您不在该群中");
        }
        List<ChatGroupMemberVO> list = chatService.getGroupMembers(groupId);
        return Result.success(list);
    }
}

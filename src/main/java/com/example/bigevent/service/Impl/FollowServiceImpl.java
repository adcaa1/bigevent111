package com.example.bigevent.service.Impl;

import com.example.bigevent.domain.User;
import com.example.bigevent.domain.vo.UserProfileVO;
import com.example.bigevent.domain.vo.UserSquareVO;
import com.example.bigevent.mapper.FollowMapper;
import com.example.bigevent.mapper.Usermapper;
import com.example.bigevent.service.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 关注关系Service实现类
 */
@Service
public class FollowServiceImpl implements FollowService {

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private Usermapper usermapper;

    /**
     * 关注用户
     * 不能关注自己，不能重复关注
     */
    @Override
    @CacheEvict(value = {"squareUsers", "mutualFriends", "userProfile"}, allEntries = true)
    public void follow(Integer userId, Integer followUserId) {
        if (userId.equals(followUserId)) {
            throw new RuntimeException("不能关注自己");
        }
        // 检查被关注用户是否存在且未注销
        User targetUser = usermapper.findById(followUserId);
        if (targetUser == null || (targetUser.getDeleted() != null && targetUser.getDeleted() == 1)) {
            throw new RuntimeException("用户不存在");
        }
        if (followMapper.isFollowed(userId, followUserId) > 0) {
            throw new RuntimeException("已关注该用户");
        }
        followMapper.addFollow(userId, followUserId);
        // 同步维护冗余计数字段
        usermapper.deltaFansCount(followUserId, 1);
        usermapper.deltaFollowCount(userId, 1);
    }

    /**
     * 取消关注
     */
    @Override
    @CacheEvict(value = {"squareUsers", "mutualFriends", "userProfile"}, allEntries = true)
    public void unfollow(Integer userId, Integer followUserId) {
        // 只有真正存在关注关系时才扣减计数
        if (followMapper.isFollowed(userId, followUserId) > 0) {
            followMapper.deleteFollow(userId, followUserId);
            usermapper.deltaFansCount(followUserId, -1);
            usermapper.deltaFollowCount(userId, -1);
        } else {
            followMapper.deleteFollow(userId, followUserId);
        }
    }

    /**
     * 判断是否已关注
     */
    @Override
    public boolean isFollowed(Integer userId, Integer followUserId) {
        return followMapper.isFollowed(userId, followUserId) > 0;
    }

    /**
     * 统计粉丝数量
     */
    @Override
    public long countFans(Integer userId) {
        return followMapper.countFans(userId);
    }

    /**
     * 统计关注数量
     */
    @Override
    public long countFollowing(Integer userId) {
        return followMapper.countFollowing(userId);
    }

    /**
     * 查询粉丝列表
     */
    @Override
    public List<User> findFans(Integer userId) {
        return followMapper.findFans(userId);
    }

    /**
     * 查询关注列表
     */
    @Override
    public List<User> findFollowing(Integer userId) {
        return followMapper.findFollowing(userId);
    }

    /**
     * 获取广场推荐用户列表（排除自己和互相关注好友）
     * sort=article 时按发布文章数降序；否则使用随机 OFFSET 随机推荐
     */
    @Override
    public List<UserSquareVO> getSquareUsers(Integer currentUserId, Integer limit, String sort) {
        if (limit == null || limit <= 0) {
            limit = 20;
        }
        // 避免前端传过大的 limit 拖慢数据库
        if (limit > 100) {
            limit = 100;
        }
        boolean byArticle = "article".equals(sort);
        List<UserSquareVO> users;
        if (byArticle) {
            // 首屏：按发布文章数降序，取第一页
            users = usermapper.findSquareUsersByArticle(currentUserId, limit, 0);
        } else {
            // 换一批：随机 offset
            Long total = usermapper.countSquareUsers(currentUserId);
            if (total == null || total == 0) {
                return List.of();
            }
            int maxOffset = (int) Math.max(0, total - limit);
            int offset = maxOffset > 0 ? new Random().nextInt(maxOffset + 1) : 0;
            users = usermapper.findRandomSquareUsers(currentUserId, limit, offset);
        }
        fillFollowedStatus(currentUserId, users);
        return users;
    }

    /**
     * 搜索广场用户（按用户名/昵称模糊搜索）
     */
    @Override
    public List<UserSquareVO> searchSquareUsers(Integer currentUserId, String keyword, Integer page, Integer pageSize) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        if (page == null || page < 1) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 20;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
        int offset = (page - 1) * pageSize;
        List<UserSquareVO> users = usermapper.searchSquareUsers(currentUserId, keyword.trim(), offset, pageSize);
        fillFollowedStatus(currentUserId, users);
        return users;
    }

    /**
     * 获取互相关注好友列表
     */
    @Override
    @Cacheable(value = "mutualFriends", key = "'mf:' + #currentUserId + ':' + (#limit != null ? #limit : 0)", unless = "#result == null")
    public List<UserSquareVO> getMutualFriends(Integer currentUserId, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 100;
        }
        if (limit > 500) {
            limit = 500;
        }
        List<UserSquareVO> friends = followMapper.findMutualFriends(currentUserId, limit);
        for (UserSquareVO vo : friends) {
            vo.setIsFollowed(true);
        }
        return friends;
    }

    /**
     * 批量填充当前用户是否已关注标记
     */
    private void fillFollowedStatus(Integer currentUserId, List<UserSquareVO> users) {
        if (users == null || users.isEmpty() || currentUserId == null) {
            return;
        }
        List<Integer> userIds = users.stream().map(UserSquareVO::getId).toList();
        List<Integer> followedIds = followMapper.batchIsFollowed(currentUserId, userIds);
        Set<Integer> followedSet = new HashSet<>(followedIds);
        for (UserSquareVO vo : users) {
            vo.setIsFollowed(followedSet.contains(vo.getId()));
        }
    }

    /**
     * 获取用户主页信息
     */
    @Override
    @Cacheable(value = "userProfile", key = "#userId + ':' + (#currentUserId != null ? #currentUserId : '0')", unless = "#result == null")
    public UserProfileVO getUserProfile(Integer userId, Integer currentUserId) {
        User user = usermapper.findById(userId);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            return null;
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setIntro(user.getIntro());
        vo.setUserPic(user.getUserPic());
        vo.setEmail(user.getEmail());
        vo.setFansCount(user.getFansCount() != null ? user.getFansCount().longValue() : 0L);
        vo.setFollowCount(user.getFollowCount() != null ? user.getFollowCount().longValue() : 0L);
        if (currentUserId != null) {
            vo.setIsFollowed(followMapper.isFollowed(currentUserId, userId) > 0);
        } else {
            vo.setIsFollowed(false);
        }
        return vo;
    }
}

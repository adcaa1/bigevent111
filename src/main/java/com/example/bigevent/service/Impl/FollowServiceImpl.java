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

import java.util.ArrayList;
import java.util.List;

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
    @CacheEvict(value = {"squareUsers", "userProfile"}, allEntries = true)
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
    }

    /**
     * 取消关注
     */
    @Override
    @CacheEvict(value = {"squareUsers", "userProfile"}, allEntries = true)
    public void unfollow(Integer userId, Integer followUserId) {
        followMapper.deleteFollow(userId, followUserId);
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
     * 获取广场用户列表
     */
    @Override
    @Cacheable(value = "squareUsers", key = "#currentUserId != null ? 'login:' + #currentUserId : 'all'", unless = "#result == null")
    public List<UserSquareVO> getSquareUsers(Integer currentUserId) {
        List<User> allUsers = usermapper.findAll();
        List<UserSquareVO> voList = new ArrayList<>();
        for (User user : allUsers) {
            // 跳过已注销用户
            if (user.getDeleted() != null && user.getDeleted() == 1) {
                continue;
            }
            // 广场不展示当前登录用户自己
            if (currentUserId != null && user.getId().equals(currentUserId)) {
                continue;
            }
            UserSquareVO vo = new UserSquareVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setUserPic(user.getUserPic());
            vo.setFansCount(followMapper.countFans(user.getId()));
            vo.setFollowCount(followMapper.countFollowing(user.getId()));
            if (currentUserId != null) {
                vo.setIsFollowed(followMapper.isFollowed(currentUserId, user.getId()) > 0);
            } else {
                vo.setIsFollowed(false);
            }
            voList.add(vo);
        }
        return voList;
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
        vo.setFansCount(followMapper.countFans(userId));
        vo.setFollowCount(followMapper.countFollowing(userId));
        if (currentUserId != null) {
            vo.setIsFollowed(followMapper.isFollowed(currentUserId, userId) > 0);
        } else {
            vo.setIsFollowed(false);
        }
        return vo;
    }
}

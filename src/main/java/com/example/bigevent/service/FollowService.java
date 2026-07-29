package com.example.bigevent.service;

import com.example.bigevent.domain.User;
import com.example.bigevent.domain.vo.UserProfileVO;
import com.example.bigevent.domain.vo.UserSquareVO;

import java.util.List;

/**
 * 关注关系Service接口
 */
public interface FollowService {

    /**
     * 关注用户
     */
    void follow(Integer userId, Integer followUserId);

    /**
     * 取消关注
     */
    void unfollow(Integer userId, Integer followUserId);

    /**
     * 判断是否已关注
     */
    boolean isFollowed(Integer userId, Integer followUserId);

    /**
     * 统计粉丝数量
     */
    long countFans(Integer userId);

    /**
     * 统计关注数量
     */
    long countFollowing(Integer userId);

    /**
     * 查询粉丝列表
     */
    List<User> findFans(Integer userId);

    /**
     * 查询关注列表
     */
    List<User> findFollowing(Integer userId);

    /**
     * 获取广场推荐用户列表（非好友，带粉丝数/关注数/是否已关注）
     * sort=article 时按发布文章数降序；否则随机推荐
     */
    List<UserSquareVO> getSquareUsers(Integer currentUserId, Integer limit, String sort);

    /**
     * 获取互相关注好友列表
     */
    List<UserSquareVO> getMutualFriends(Integer currentUserId, Integer limit);

    /**
     * 获取用户主页信息
     */
    UserProfileVO getUserProfile(Integer userId, Integer currentUserId);
}

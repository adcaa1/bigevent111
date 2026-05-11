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
     * 获取广场用户列表（带粉丝数/关注数/是否已关注）
     */
    List<UserSquareVO> getSquareUsers(Integer currentUserId);

    /**
     * 获取用户主页信息
     */
    UserProfileVO getUserProfile(Integer userId, Integer currentUserId);
}

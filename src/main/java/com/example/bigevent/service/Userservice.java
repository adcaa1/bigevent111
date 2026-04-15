package com.example.bigevent.service;

import com.example.bigevent.domain.User;

public interface Userservice {
    User findid(String username);

    int add(String username, String password);

    void update(User user);

    void updatetx(String txurl);

    void updatepwd(String newpwd1);
}

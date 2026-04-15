package com.example.bigevent.service.Impl;

import com.example.bigevent.domain.User;
import com.example.bigevent.mapper.Usermapper;
import com.example.bigevent.service.Userservice;
import com.example.bigevent.util.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserserviceImpl implements Userservice {

    @Autowired
    private Usermapper usermapper;
    @Override
    public User findid(String username) {
        return usermapper.findid(username);
    }

    @Override
    public int add(String username, String password) {
        return usermapper.add(username,password);
    }

    @Override
    public void update(User user) {
        usermapper.update(user);
    }

    @Override
    public void updatetx(String txurl) {
        Map<String,Object> claims = ThreadLocalUtil.get();
        Integer id = (Integer) claims.get("id");
        usermapper.updatetx(txurl,id);
    }

    @Override
    public void updatepwd(String newpwd) {
        Map<String,Object> claims = ThreadLocalUtil.get();
        Integer id = (Integer) claims.get("id");
        usermapper.updatepwd(newpwd, id);
    }

}

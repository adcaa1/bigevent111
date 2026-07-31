package com.example.bigevent;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class jwttest {
    @Test
    public void test() {
        Map<String, Object> map = new HashMap<>();
        map.put("username", "admin");
        map.put("password", "123456");
        String s = JWT.create()
                .withClaim("user", map)//设置用户信息
                .withExpiresAt(new Date(System.currentTimeMillis() + 1000 * 60 * 60))//设置过期时间,是以毫秒为单位
                .sign(Algorithm.HMAC256("123456"));//设置密钥
        log.info("生成的 JWT token: {}", s);

    }
}

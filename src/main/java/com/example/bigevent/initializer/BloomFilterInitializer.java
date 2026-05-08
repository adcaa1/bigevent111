package com.example.bigevent.initializer;

import com.example.bigevent.mapper.Usermapper;
import com.example.bigevent.util.BloomFilterUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BloomFilterInitializer implements CommandLineRunner {

    @Autowired
    private BloomFilterUtil bloomFilterUtil;

    @Autowired
    private Usermapper usermapper;

    @Override
    public void run(String... args) {
        // 如果布隆过滤器里已经有数据，说明Redis持久化还在，直接跳过
        if (bloomFilterUtil.isUsernameFilterExists()) {
            System.out.println("[BloomFilter] 已有数据，跳过预热");
            return;
        }
        // 预热用户名
        List<String> usernames = usermapper.findAllUsernames();
        if (usernames != null && !usernames.isEmpty()) {
            for (String username : usernames) {
                bloomFilterUtil.addUsername(username);
            }
        }
        System.out.println("[BloomFilter] 用户名预热完成，共加载 " + (usernames == null ? 0 : usernames.size()) + " 条");
    }
}

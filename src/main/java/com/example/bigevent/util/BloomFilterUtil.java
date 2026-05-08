package com.example.bigevent.util;

import jakarta.annotation.PostConstruct;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BloomFilterUtil {

    @Autowired
    private RedissonClient redissonClient;

    private static final String USERNAME_BLOOM_FILTER = "bloomfilter:username";

    // 预计插入量与误判率
    private static final long EXPECTED_INSERTIONS_USERNAME = 100000L;
    private static final double FALSE_PROBABILITY = 0.01;

    @PostConstruct
    public void init() {
        RBloomFilter<String> usernameFilter = redissonClient.getBloomFilter(USERNAME_BLOOM_FILTER);
        usernameFilter.tryInit(EXPECTED_INSERTIONS_USERNAME, FALSE_PROBABILITY);
    }

    public void addUsername(String username) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(USERNAME_BLOOM_FILTER);
        bloomFilter.add(username);
    }

    public boolean mightContainUsername(String username) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(USERNAME_BLOOM_FILTER);
        return bloomFilter.contains(username);
    }
    public boolean isUsernameFilterExists() {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(USERNAME_BLOOM_FILTER);
        return bloomFilter.isExists() && bloomFilter.count() > 0;
    }
}

package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author ZC_Wu 汐
 * @date 2024/12/13 16:21
 * @description 基于redis的id生成器
 */
@Component
public class RedisIdWorker {

    // 开始的时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    // 序列号位数
    private static final long COUNT_BITS = 32L;

    private StringRedisTemplate stringRedisTemplate;
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 使用redis生成全局唯一id
     * 传一个前缀区分不同业务
     * @param keyPrefix 业务前缀
     * @return
     */
    public long nextId(String keyPrefix) {
        // 1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 生成序列号
        // 2.1 获取当前日期，精确到日
        String data = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 2.2 自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + data); // 不存在会自动创建一个key

        // 3. 拼接并返回
        return timestamp << COUNT_BITS | count; // 先将时间戳左移空出位置来，然后用或运算(左移空出来的位置上都为0，count的每个位置上不管是几和0做或运算都是count原来的值)拼上count的值
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second); // second = 1640995200
    }
}

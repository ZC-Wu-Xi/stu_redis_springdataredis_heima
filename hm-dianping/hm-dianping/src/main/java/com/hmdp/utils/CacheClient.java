package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author ZC_Wu 汐
 * @date 2024/11/16 15:45
 * @description
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 设置TTL的写
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 设置逻辑过期时间的写
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value));
    }

    /**
     * 根据id查询
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     * @param keyPrefix key的前缀
     * @param id 需要查询的某个东西的id
     * @param type 查询的那个东西的类
     * @param dbFallback 查询逻辑 函数式
     * @param time 过期时间
     * @param unit 过期时间单位
     * @return 要查的东西
     * @param <R> 查询的东西的类型
     * @param <ID> id的类型
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 从 redis 查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(json)) { // 不为空
            // 3. 存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        // 判断缓存命中的是否是空值 如果是空值则是之前写入的数据，证明是缓存穿透数据
        if (json != null) { // 查到了 且!=null 此时为“”，即缓存穿透数据
            return null;
        }

        // 4. redis不存在，查询数据库
        // 执行传过来的逻辑
        R r = dbFallback.apply(id);

        // 5. 数据库不存在返回错误
        if (r == null) {
            // 缓存穿透问题解决方式 将空值(空字符串)写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6. 存在 将结果写入 redis
        this.set(key, r, time, unit);
        // 7. 返回
        return r;
    }


    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * 根据id查询
     * 逻辑过期解决缓存击穿 需要先进行缓存预热提前将热点key存入redis
     * @param keyPrefix key的前缀
     * @param id 需要查询的某个东西的id
     * @param type 查询的那个东西的类
     * @param dbFallback 查询逻辑 函数式
     * @param time 逻辑过期时间
     * @param unit 逻辑过期时间单位
     * @return 要查的东西
     * @param <R> 查询的东西的类型
     * @param <ID> id的类型
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit ) {
        String key = keyPrefix + id;
        // 1. 从 redis 查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3. 不存在，直接返回
            return null;
        }

        // 4. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject jsonObject = (JSONObject)redisData.getData();
        R r = JSONUtil.toBean(jsonObject, type);
        // 过期时间
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) { // 过期时间是否在当前时间之后
            // 5.1 未过期，直接返回店铺信息
            return r;
        }
        // 5.2 已过期，需要缓存重建
        // 6. 缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断获取锁是否成功
        if (isLock) {
            // 6.3 成功，开启独立线程执行重建过程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    // 查数据库
                    R r1 = dbFallback.apply(id);
                    // 写redis 可以设置逻辑过期时间
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.3 返回过期的店铺信息
        return r;
    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        // SET key "1" NX EX 10  如果不存在key则新建key:1，过期时间为10s  值是1(随便设的)
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag); // 拆箱过程中有可能出现空指针Boolean->boolean，因此使用该工具类
    }

    /**
     * 释放锁
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }



}

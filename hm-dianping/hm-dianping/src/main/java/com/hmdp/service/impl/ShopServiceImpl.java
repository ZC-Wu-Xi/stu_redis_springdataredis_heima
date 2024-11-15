package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);

        // 缓存穿透 + 缓存击穿解决
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 7. 返回
        return Result.ok(shop);
    }

    /**
     * 根据id查询店铺
     * 缓存击穿 + 缓存穿透
     * 互斥锁解决缓存击穿 且 缓存空对象解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 从 redis 查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) { // 不为空
            // 3. 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断缓存命中的是否是空值 如果是空值则是之前写入的数据，证明是缓存穿透数据
        if (shopJson != null) { // 查到了 且!=null 此时为“”，即缓存穿透数据
            return null;
        }

        // 4. 实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);

            // 4.2 获取是否获取成功 即使获取锁成功也有可能
            if (!isLock) {
                // 4.3 失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // A线程恢复Redis成功时,B线程的运行进度可能已经超过了第一次判断Redis中是否存在数据,而让B线程获取到释放的锁如果不再次判断,B线程会以为Redis并未修复,于是继续访问数据库
            // 因此获取锁成功后,再次判断Redis是否存在,若存在则无需重建Redis,直接返回即可
            if ( StrUtil.isNotBlank(shopJson)) {
                System.out.println("Redis存在,直接返回");
//3. 若存在则直接返回
                return JSONUtil.toBean(shopJson, Shop.class);
            }

            // 4.4. redis不存在，查询数据库，
            shop = getById(id);
            // 模拟重建缓存耗时
            Thread.sleep(20000);

            // 5. 数据库不存在返回错误
            if (shop == null) {
                // 缓存穿透问题解决方式 将空值(空字符串)写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 6. 存在 将结果写入 redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
            // 过期时间 30min
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7. 释放互斥锁
            unlock(lockKey);
        }
        // 8. 返回
        return shop;
    }

    /**
     * 根据id查询店铺
     * 缓存穿透：客户端请求的数据在缓存中和数据库中都不存在，这样缓存永远不会生效，这些请求都会打到数据库。
     * 缓存空对象解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 从 redis 查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) { // 不为空
            // 3. 存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class); // json转为shop对象
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断缓存命中的是否是空值 如果是空值则是之前写入的数据，证明是缓存穿透数据
        if (shopJson != null) { // 查到了 且!=null 此时为“”，即缓存穿透数据
            return null;
        }

        // 4. redis不存在，查询数据库，
        Shop shop = getById(id);

        // 5. 数据库不存在返回错误
        if (shop == null) {
            // 缓存穿透问题解决方式 将空值(空字符串)写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6. 存在 将结果写入 redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        // 过期时间 30min
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 7. 返回
        return shop;
    }

    /**
     * 尝试获取锁
     * 利用互斥锁解决缓存击穿问题
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
     * 用互斥锁解决缓存击穿问题
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1. 更新数据库
        updateById(shop);

        // 2， 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        String key = CACHE_SHOP_KEY + id;
        // 1. 从 redis 查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) { // 不为空
            // 3. 存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class); // json转为shop对象
            return Result.ok(shop);
        }

        // 4. redis不存在，查询数据库，
        Shop shop = getById(id);

        // 5. 数据库不存在返回错误
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        // 6. 存在 将结果写入 redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));

        // 7. 返回
        return Result.ok(shop);
    }
}

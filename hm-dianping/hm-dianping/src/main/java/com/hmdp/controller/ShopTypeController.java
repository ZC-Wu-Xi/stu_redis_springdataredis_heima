package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY;
        /*
        // 使用 string 类型 从 redis 中查缓存
        String shopList = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopList)) { // 不为空
            List<ShopType> typeList = JSONUtil.toList(shopList, ShopType.class);
            return Result.ok(typeList);
        }
        */

        // 1. 使用 list 类型 从 redis 中查缓存
        List<String> shopTypeListInRedis = stringRedisTemplate.opsForList().range(key, 0, -1);
        // 2. 判断是否存在
        if (!shopTypeListInRedis.isEmpty()) {
            // 存在就返回
            List<ShopType> shopTypeList = shopTypeListInRedis.stream().map(item -> {
                return JSONUtil.toBean(item, ShopType.class);
            }).collect(Collectors.toList());

            return Result.ok(shopTypeList);
        }
        // 3. 不存在就查数据库
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        List<String> redisList = typeList.stream().map(item -> {
            return JSONUtil.toJsonStr(item);
        }).collect(Collectors.toList());

        //5.将查询出来写入redis
        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY, redisList);
        /*
        // 使用 string 类型 从 redis 中放缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList), 60 * 24 * 15, TimeUnit.MINUTES);
        */
        return Result.ok(typeList);
    }
}

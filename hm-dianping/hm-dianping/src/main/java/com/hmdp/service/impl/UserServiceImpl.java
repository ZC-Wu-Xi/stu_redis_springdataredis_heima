package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate; // SpringDataRedis提供的 api

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

//        // 4. 将验证码保存到session
//        session.setAttribute("code", code);
        // 4. 将验证码保存到redis  set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES); // 过期时间两分钟

        // 5. 发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);

        // 返回 ok
        return Result.ok();
    }

    /**
     * 登录功能 若未注册进行注册
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 不一致报错
            return Result.fail("手机号格式错误!");
        }

        // 3. 校验验证码
        String code = loginForm.getCode();
//        // 从session中获取验证码
//        String sessionCode = (String) session.getAttribute("code");
        // 从redis中获取验证码
        String sessionCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (sessionCode == null || !sessionCode.equals(code)) {
            // 4. 不一致报错
            return Result.fail("验证码错误!");
        }
        // 5. 一致，根据手机号查询用户信息
        User user = query().eq("phone", phone).one();

        // 6. 判断用户是否存在
        if (user == null) {
            // 7. 不存在则新建用户并保存
            user = createUserWithPhone(phone);
        }
//        // 8. 保存用户信息到 session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        // 8. 保存用户信息到 redis
        // 8.1 声明一个 token 作为登陆令牌
        String token = UUID.randomUUID().toString(true);
        // 8.2 将 User 转为 Hash 存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue != null ? fieldValue.toString() : null)); // 把DTO转为map，并在转的时候忽略空值把键变为字符串

        // 8.3 保存到 redis 中
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 8.4 设置 token 的过期时间
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 9. 返回 token
        return Result.ok(token);
    }


    /**
     * 根据手机号创建用户并保存到用户表
     *
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        // 1. 创建用户
        User user = new User();
        // 2. 设置手机号
        user.setPhone(phone);
        // 3. 生成随机昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        // 4. 保存用户
        save(user);// 保存到数据库
        return user;
    }
}

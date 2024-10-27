package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author ZC_Wu 汐
 * @date 2024/10/26 21:05
 * @description 登陆校验拦截器 拦截需要登陆才能访问的路径
 * 查询用户 存在放行，不存在拦截
 */
public class LoginInterceptor implements HandlerInterceptor {
    /*private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }*/

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*
//        // 1. 获取 session
//        HttpSession session = request.getSession();
//        // 2. 判断 session 中是否有用户
//        Object user = session.getAttribute("user");
//        // 3. 判断用户是否存在
//        if (user == null) {
//            // 4. 不存在，拦截
//            response.setStatus(401); // 401 未授权
//            return false;
//        }

        // 1. 获取 请求头中的 token
        String token = request.getHeader("authorization");

        if (StrUtil.isBlank(token)) { // token 为空
            // 不存在，拦截
            response.setStatus(401); // 401 未授权
            return false;
        }

        // 2. 基于 token 获取redis中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        // 3. 判断用户是否存在
        if (userMap.isEmpty()) {
            // 不存在，拦截
            response.setStatus(401); // 401 未授权
            return false;
        }

        // 5. 将查询到的 Hash 数据转为 UserDTO 对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 6. 存在，保存用户到 ThreadLocal
        UserHolder.saveUser(userDTO);

        // 7. 刷新 token 的有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8. 放行
        */
        // 1. 判断是否需要拦截（ThreadLocal 中是否有用户)
        if (UserHolder.getUser() == null) {
            // 不存在，拦截
            response.setStatus(401); // 401 未授权
            return false;
        }
        return true;
    }

/*    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }*/
}

package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author ZC_Wu 汐
 * @date 2024/10/26 21:21
 * @description
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 添加拦截器
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code", "/user/login", "/shop/**", "/voucher/**", "/shop-type/**", "/upload/**", "/blog/hot", "/blog/**")
                .order(1); // 排除拦截，不对这些进行拦截
        // 添加刷新token拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0); // order值越小，优先级越高，默认都为0
    }
}

package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    // 逻辑过期时间
    private LocalDateTime expireTime;
    // 其他的实体
    private Object data;
}

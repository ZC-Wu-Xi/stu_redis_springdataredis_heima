package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

/**
 * 登录校验 dto
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO userDTO){
        tl.set(userDTO);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}

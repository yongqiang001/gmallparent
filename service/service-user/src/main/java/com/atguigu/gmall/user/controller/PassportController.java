package com.atguigu.gmall.user.controller;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.IpUtil;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;


import jodd.time.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user/passport")
public class PassportController {
    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;


    // 从页面入手：登录页面login.html 得知前台页面传递Json 数据。
    // 页面需要返回code，那么Result
    // 登录控制器
    // 登录
    @PostMapping("login")
    public Result login(@RequestBody UserInfo userInfo,
                        HttpServletRequest request,
                        HttpServletResponse response) {

        System.out.println("进入控制器!");
        // 调用服务层方法
        UserInfo info = userService.login(userInfo);
        if (info != null) {
            // 获取token   制作一个uuid
            String token = UUID.randomUUID().toString().replaceAll("-", "");
            HashMap<String, Object> map = new HashMap<>();
            // 将我们的数据写入map
            map.put("nickName", info.getNickName());
            map.put("token", token);

            // 存储的用户信息
            // 先存储一个userId{判断是否登录},再存储一个ip{为了保证登录的时候，是在同一台电脑}
            JSONObject userJson = new JSONObject();
            userJson.put("userId", info.getId().toString());
            userJson.put("ip", IpUtil.getIpAddress(request));
            redisTemplate.opsForValue().set(RedisConst.USER_LOGIN_KEY_PREFIX + token,
                    userJson.toJSONString(),
                    RedisConst.USERKEY_TIMEOUT,
                    TimeUnit.SECONDS);
            // 返回数据 由js 将我们token 放入了cookie 中！
            return Result.ok(map);

        } else {
            // 说明登录不成功
            return Result.fail().message("用户名或密码错误");
        }

    }

    // 退出登录
    @GetMapping("logout")
    public Result logout(HttpServletRequest request) {
        // 退出登录，删除缓存的数据，要想删除数据必须获取到token中的uuId
        // String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+uuid;
        // uuId 在登录的时候，放入了cookie，同时还放入了header 中
        redisTemplate.delete(RedisConst.USER_LOGIN_KEY_PREFIX + request.getHeader("token"));

        return Result.ok();
    }

}

package com.atguigu.gmall.user.service.impl;

import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.mapper.UserInfoMapper;
import com.atguigu.gmall.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    public UserInfo login(UserInfo userInfo) {
        // 这个userInfo.getPasswd() 是明文，数据库应该是加密
        String passwd = userInfo.getPasswd();
        // 这个密码是经过MD5加密之后的
        String newPasswd = DigestUtils.md5DigestAsHex(passwd.getBytes());
        //一个用户名，密码在数据库表中应该只有一个 select * from user_info where login_name=? and passwd=?
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("login_name", userInfo.getLoginName());
        queryWrapper.eq("passwd", newPasswd);
        UserInfo info = userInfoMapper.selectOne(queryWrapper);
        // 返回登录之后的对象
        return info;
    }
}

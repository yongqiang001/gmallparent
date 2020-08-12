package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class PassportController {

    // http://passport.gmall.com/login.html?originUrl='+window.location.href
    @GetMapping("login.html")
    public String login(HttpServletRequest request){
        // 记录用户是从什么位置点击的登录
        String originUrl = request.getParameter("originUrl");
        request.setAttribute("originUrl", originUrl);
        return "login";
    }
}

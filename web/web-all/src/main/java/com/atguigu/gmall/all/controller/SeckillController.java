package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.jws.WebParam;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;

@Controller
public class SeckillController {
    @Autowired
    private ActivityFeignClient activityFeignClient;

    // 秒杀列表
    @GetMapping("seckill.html")
    public String index(Model model){
        Result result = activityFeignClient.findAll();
        model.addAttribute("list", result.getData());
        return "seckill/index";
    }

    // 秒杀详情
    @GetMapping("seckill/{skuId}.html")
    public String getItem(@PathVariable Long skuId, Model model){
        // 通过skuId查询skuInfo
        Result result = activityFeignClient.getSeckillGoods(skuId);
        model.addAttribute("item", result.getData());
        return "seckill/item";

    }

    // 秒杀排队
    @GetMapping("seckill/queue.html")
    public String queue(@RequestParam(name = "skuId") Long skuId,
                        @RequestParam(name = "skuIdStr") String skuIdStr,
                        HttpServletRequest request){
        request.setAttribute("skuId", skuId);
        request.setAttribute("skuIdStr", skuIdStr);
        return "seckill/queue";
    }
    // 确认订单
    @GetMapping("seckill/trade.html")
    public String trade(Model model){
        Result<Map<String, Object>> result = activityFeignClient.trade();
        if (result.isOk()){
            model.addAllAttributes(result.getData());
            return  "seckill/trade";
        }else {
            model.addAttribute("message", result.getMessage());
            return "seckill/fail";
        }

    }
}

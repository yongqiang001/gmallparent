package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class PaymentController {

    @Autowired
    private OrderFeignClient orderFeignClient;

    /**
     * 支付页
     * @param request
     * @return
     */
    @GetMapping("pay.html")
    public String success(HttpServletRequest request, Model model) {
        String orderId = request.getParameter("orderId");
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(Long.parseLong(orderId));
        // 需要存储一个orderInfo 对象
        model.addAttribute("orderInfo", orderInfo);
        return "payment/pay";
    }

    /**
     * 支付成功页
     * @param request
     * @return
     */
    @GetMapping("pay/success.html")
    public String success() {
        return "payment/success";
    }
}

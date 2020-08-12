package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("api/cart")
public class CartApiController {

    // 引入服务层
    @Autowired
    private CartService cartService;

    // 添加购物车
    // 将这个数据接口提供到feign上, 然后将其数据发送给web-all中的addCart.html控制器
    @PostMapping("addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable Long skuId,
                            @PathVariable Integer skuNum,
                            HttpServletRequest request) {
        // 如何获取到userId，在网关中将用户Id{登录，未登录}传递到后台
        // 在网关中将用户id 保存到了header 中！
        // 获取登录的userId
        String userId = AuthContextHolder.getUserId(request);
        // 说明用户未登录
        if (StringUtils.isEmpty(userId)) {
            // 获取临时用户Id
            userId = AuthContextHolder.getUserTempId(request);
        }

        // 调用添加方法
        cartService.addToCart(skuId, userId, skuNum);
        return Result.ok();
    }

    // 查询购物车列表控制器
    @GetMapping("cartList")
    public Result cartList(HttpServletRequest request) {
        // 调用服务层方法
        String userId = AuthContextHolder.getUserId(request);
        String userTempId = AuthContextHolder.getUserTempId(request);
        List<CartInfo> cartList = cartService.getCartList(userId, userTempId);
        return Result.ok(cartList);
    }

    @GetMapping("checkCart/{skuId}/{isChecked}")
    public Result checkCart(@PathVariable Long skuId,
                            @PathVariable Integer isChecked,
                            HttpServletRequest request) {

        // 获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)) {
            // 获取未登录的临时用户Id
            userId = AuthContextHolder.getUserTempId(request);
        }
        // 调用服务层方法
        cartService.checkCart(userId, isChecked, skuId);
        // 返回
        return Result.ok();
    }

    // 删除购物车
    @DeleteMapping("deleteCart/{skuId}")
    public Result deleteCart(@PathVariable Long skuId,
                             HttpServletRequest request) {
        // 获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)) {
            // 获取未登录的用户Id
            userId = AuthContextHolder.getUserTempId(request);
        }
        // 调用服务层方法
        cartService.deleteCartInfo(userId, skuId);
        return Result.ok();
    }


    // 封装控制器
    @GetMapping("getCartCheckedList/{userId}")
    public List<CartInfo> getCartCheckedList(@PathVariable String userId){
        // 返回数据
        return cartService.getCartCheckedList(userId);
    }

    /**
     *根据用户Id查询购物车最新数据并放入缓存
     * @param userId
     * @return
     */
    @GetMapping("loadCartCache/{userId}")
    public Result loadCartCache(@PathVariable("userId") String userId) {
        cartService.loadCartCache(userId);
        return Result.ok();
    }


}

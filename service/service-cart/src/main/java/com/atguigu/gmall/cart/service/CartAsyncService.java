package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.stereotype.Service;


public interface CartAsyncService {
    // 编写一个更新方法
    void updateCartInfo(CartInfo cartInfo);
    // 编写一个插入方法
    void saveCartInfo(CartInfo cartInfo);
    // 编写一个删除方法
    void deleteCartInfo(String userId);
    // 编写一个更新状态方法
    void checkCart(String userId,Integer isChecked,Long skuId);
    // 删除购物车数据
    void deleteCartInfo(String userId,Long skuId);
}

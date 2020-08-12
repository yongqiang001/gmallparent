package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

import java.util.List;

public interface CartService {
    // 添加购物车 用户Id，商品Id，商品数量。
    void addToCart(Long skuId, String userId, Integer skuNum);

    /**
     * 通过用户Id 查询购物车列表
     * @param userId
     * @param userTempId
     * @return
     */
    List<CartInfo> getCartList(String userId, String userTempId);

    void checkCart(String userId, Integer isChecked, Long skuId);

    void deleteCartInfo(String userId, Long skuId);

    // 根据用户Id 查询购物车列表
    List<CartInfo> getCartCheckedList(String userId);
    // 根据用户Id查询购物车最新数据并放入缓存
    List<CartInfo> loadCartCache(String userId);
}

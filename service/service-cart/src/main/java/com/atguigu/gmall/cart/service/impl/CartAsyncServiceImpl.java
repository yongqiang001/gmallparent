package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.model.cart.CartInfo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CartAsyncServiceImpl implements CartAsyncService {

    @Autowired
    private CartInfoMapper cartInfoMapper;
    // 更新购物车
    @Override
    @Async
    public void updateCartInfo(CartInfo cartInfo) {
        System.out.println("update---------");
        cartInfoMapper.updateById(cartInfo);


    }

    // 保存购物车
    @Override
    @Async
    public void saveCartInfo(CartInfo cartInfo) {
        System.out.println("insert---------");
        cartInfoMapper.insert(cartInfo);
    }

    @Override
    @Async
    public void deleteCartInfo(String userId) {
        // 删除方法
        System.out.println("delete -----");
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id",userId));
    }

    @Override
    @Async
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        System.out.println("update --- userId --- isChecked --- skuId");
        // cartInfoMapper.updateById() // 有主键
        // 第一个参数表示更新的内容，第二个参数表示更新的条件
        // update cart_info set is_checked = isChecked where user_id = userId and sku_id=skuId;
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("user_id",userId).eq("sku_id",skuId);
        cartInfoMapper.update(cartInfo,cartInfoQueryWrapper);
    }

    @Override
    @Async
    public void deleteCartInfo(String userId, Long skuId) {
        // 编写实现
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("sku_id",skuId).eq("user_id",userId));
    }
}

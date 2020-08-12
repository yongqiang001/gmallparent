package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author mqx
 * @date 2020-8-7 15:51:41
 */
@Service
public class CartServiceImpl implements CartService {
    // 引入mapper
    @Autowired
    private CartInfoMapper cartInfoMapper;

    // 引入异步操作数据库的接口
    @Autowired
    private CartAsyncService cartAsyncService;

    @Autowired
    private ProductFeignClient productFeignClient;

    // 引入缓存
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        /*
        1.  如果购物车中没有当前要添加的商品，则数据直接插入！
        2.  如果购物车中有当前要添加的商品，则数量直接相加
        3.  做一步：mysql 与 redis 做同步！
        弊端： mysql 与 redis 相当于同步操作的！
         */
        // 获取购物车的key
        String cartKey = getCartKey(userId);
        // 缓存根本没有数据
        if (!redisTemplate.hasKey(cartKey)){
            loadCartCache(userId);
        }
        // 查询购物车中是否有该商品！ 查询数据库
        // 在购物车表中，一个用户，购买同一件商品，那么在数据库表中只有唯一的一条记录！
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("sku_id",skuId);
        cartInfoQueryWrapper.eq("user_id",userId);
        CartInfo cartInfoExist = cartInfoMapper.selectOne(cartInfoQueryWrapper);
        // 说明购物车中有添加的商品
        if (cartInfoExist!=null){
            // 有的话，商品的数量相加
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum()+skuNum);
            // 初始化一个实时价格
            cartInfoExist.setSkuPrice(productFeignClient.getSkuPrice(skuId));
            // 更新数据库,缓存。暂时，先这样写，后面会有优化！
            // cartInfoMapper.updateById(cartInfoExist);
            cartAsyncService.updateCartInfo(cartInfoExist);
            //  更新缓存
            //  redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfoExist);

        }else {
            // 说明购物车中没有添加的商品
            CartInfo cartInfo = new CartInfo();
            // 给cartInfo 赋值数据 购物车---商品详情---商品后台{service-product}
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            // 实时价格 =相当于skuInfo.price(); 第一次添加购物车的时候，这个价格就是实时价格。
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setSkuNum(skuNum);
            // 默认值1 表示被选中
            // cartInfo.setIsChecked();
            cartInfo.setUserId(userId);
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setSkuId(skuId);
            // 添加购物车时的价格，默认是最新的价格
            cartInfo.setCartPrice(skuInfo.getPrice());
            // 插入到数据库
            // cartInfoMapper.insert(cartInfo);
            cartAsyncService.saveCartInfo(cartInfo);

            cartInfoExist = cartInfo;
            //  更新缓存
            //  redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfo);

        }
        // 要设置缓存同步

        // 使用hash 数据类型存储。
        /*
        hset(key,field,value);
        key=user:userId:cart
        field=skuId
        value=cartInfo.toString();
         */
        //        redisTemplate.opsForHash().put();
        // 废物利用！
        redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfoExist);

        // 设置购物车过期时间
        setCartKeyExpire(cartKey);

    }

    @Override
    public List<CartInfo> getCartList(String userId, String userTempId) {
        // 声明一个集合对象List<CartInfo>
        List<CartInfo> cartInfoList = new ArrayList<>();
        // 判断用户是否登录
        if (StringUtils.isEmpty(userId)){
            // 登录的userId 为空，那么应该获取临时userTempId的购物车
            cartInfoList = getCartList(userTempId);
        }
        // 登录
        if (!StringUtils.isEmpty(userId)){
            // 只有在用户登录的情况下 才能合并购物车！
            List<CartInfo> cartInfoNoLoginList = getCartList(userTempId);
            if (!CollectionUtils.isEmpty(cartInfoNoLoginList)){
                // 未登录购物车中有数据，则合并。
                cartInfoList = mergeToCartList(cartInfoNoLoginList,userId);
                // 合并完成之后，删除未登录购物车
                deleteCartList(userTempId);
            }
            // 细节问题：如果未登录数据是空 或者 userTempId 是空，都回直接查询数据库
            if (CollectionUtils.isEmpty(cartInfoNoLoginList) || StringUtils.isEmpty(userTempId)){
                // 登录的userId 不为空， 查询userId 购物车
                cartInfoList = getCartList(userId);
            }
        }
        return cartInfoList;
    }

    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        // 这个位置，你要操作mysql，redis
        // 异步操作数据库：
        cartAsyncService.checkCart(userId, isChecked, skuId);
        // 更新redis
        // 获取到key
        String cartKey = getCartKey(userId);
        /*
            hset(key,field,value);
            获取可以所有的数据
            BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
            通过key ，field 获取value
            hget(key,field);
            CartInfo cartInfoUpd = (CartInfo) boundHashOperations.get(skuId.toString());
            放入数据 field,value
            boundHashOperations.put(skuId.toString(),cartInfoUpd);
         */

        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
        // 判断这个hash 中是否有对应的key
        if (boundHashOperations.hasKey(skuId.toString())){
            // 准备更新数据
            CartInfo cartInfoUpd = (CartInfo) boundHashOperations.get(skuId.toString());
            cartInfoUpd.setIsChecked(isChecked);
            // 将更新对象写入缓存
            boundHashOperations.put(skuId.toString(),cartInfoUpd);
            // 设置一下获取时间
            setCartKeyExpire(cartKey);
        }
    }

    @Override
    public void deleteCartInfo(String userId, Long skuId) {
        // 操作mysql，redis
        cartAsyncService.deleteCartInfo(userId, skuId);
        // 先获取购物车key
        String cartKey = getCartKey(userId);
        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
        if (boundHashOperations.hasKey(skuId.toString())){
            // 删除数据
            boundHashOperations.delete(skuId.toString());
        }
    }

    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        // 声明一个集合来存储被选中的商品
        List<CartInfo> cartInfosList = new ArrayList<>();
        // 从购物车列表页面点击去结算，这个时候，缓存一定会有数据！
        // 所以直接获取缓存数据即可！
        // 获取购物车key
        String cartKey = getCartKey(userId);
        // 获取购物车数据集合
        List<CartInfo> cartInfoList = redisTemplate.opsForHash().values(cartKey);
        if (!CollectionUtils.isEmpty(cartInfoList)){
            // 循环遍历 获取被选中的数据
            for (CartInfo cartInfo : cartInfoList) {
                if (cartInfo.getIsChecked().intValue()==1){
                    cartInfosList.add(cartInfo);
                }
            }
        }
        // 返回集合
        return cartInfosList;
    }

    // 删除未登录购物车数据
    private void deleteCartList(String userTempId) {
        // 删除数据库，还需要删除缓存
        // 将其改成异步
        // cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id",userTempId));
        // 调用异步方法
        cartAsyncService.deleteCartInfo(userTempId);
        // 获取缓存的key
        String cartKey = getCartKey(userTempId);
        // 判断缓存有这个keyme
        Boolean flag = redisTemplate.hasKey(cartKey);
        if (flag){
            redisTemplate.delete(cartKey);
        }
    }

    // 合并购物车数据
    private List<CartInfo> mergeToCartList(List<CartInfo> cartInfoNoLoginList, String userId) {
        /*
        实现思路：
            demo1:
        登录：
            37 1
            38 1
        未登录：
            37 1
            38 1
            39 1
        合并之后的数据
            37 2
            38 2
            39 1
         demo2:
             未登录：
                37 1
                38 1
                39 1
                40 1
              合并之后的数据
                37 1
                38 1
                39 1
                40 1
         */
        // 合并必须有两个集合： 登录购物车集合，未登录购物车集合
        List<CartInfo> cartInfoLoginList = getCartList(userId);
        // cartInfoNoLoginList 未登录购物车
        // 将登录购物车变为map结构 key=skuId value=CartInfo
        // Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo)
        // map.put(key,value);
        // map.put(cartinfo.getskuId,cartinfo)
        // Function 当参数只有一个参数的时候，() {} 都可以省略。
        Map<Long, CartInfo> cartInfoLoginMap = cartInfoLoginList.stream().collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));
        // 处理map
        // 循环遍历未登录购物车集合数据
        for (CartInfo cartInfoNoLogin : cartInfoNoLoginList) {
            // 获取未登录购物车数据对象中的skuId.
            Long skuId = cartInfoNoLogin.getSkuId();
            // 判断 登录和未登录有相同商品
            if(cartInfoLoginMap.containsKey(skuId)){
                // 商品的数量进行相加操作 37,38
                CartInfo cartInfoLogin = cartInfoLoginMap.get(skuId);
                cartInfoLogin.setSkuNum(cartInfoLogin.getSkuNum()+cartInfoNoLogin.getSkuNum());
                // 细节： 合并选中细节
                // 未登录购物车选中状态，那么则数据库为选中状态
                if (cartInfoNoLogin.getIsChecked().intValue()==1){
                    cartInfoLogin.setIsChecked(1);
                }
                // 更新数据库操作
                cartAsyncService.updateCartInfo(cartInfoLogin);
            }else {
                // 没有包含的说明没有对应的商品，cartInfoNoLogin 放入数据  39
                // 将未登录用户Id 设置成已登录的用户Id
                cartInfoNoLogin.setUserId(userId);
                cartAsyncService.saveCartInfo(cartInfoNoLogin);
            }
        }
        // 获取合并之后的所有数据  37,38，39 统一做个汇总，去查询一下最新购物车数据。
        List<CartInfo> cartInfoList = loadCartCache(userId);
        // 返回数据
        return cartInfoList;
    }


    // 根据userId 获取购物车列表数据
    private List<CartInfo> getCartList(String userId) {
        // 声明一个集合
        List<CartInfo> cartInfoList = new ArrayList<>();
        // 判断用户Id是否为空
        if (StringUtils.isEmpty(userId)){
            return cartInfoList;
        }
        // 先从缓存中获取
        // 获取购物车key
        String cartKey = getCartKey(userId);
        // 获取购物车中所有数据？ 获取hash中所有的value 数据
        cartInfoList = redisTemplate.opsForHash().values(cartKey);
        // 判断
        if(!CollectionUtils.isEmpty(cartInfoList)){
            // 循环遍历获取里面的数据，原因？查询购物车列表时，应该有排序功能！ 按照商品的更新时间进行排序
            // 由于我们这个table 没有更新时间字段 | 在此按照id 进行排序。
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    return o1.getId().compareTo(o2.getId());
                }
            });
            // 返回排序好的集合数据
            return cartInfoList;
        }else {
            // 如果缓存数据为空，那么应该如何处理？ 获取数据库中的数据，并添加到缓存
            cartInfoList = loadCartCache(userId);
            return cartInfoList;
        }

    }

    // 根据userId 查询数据库，并将结果放入缓存！
    public List<CartInfo> loadCartCache(String userId) {
        // 从数据库中获取数据
        // select * from cart_info where user_id=userId
        List<CartInfo> cartInfoList = cartInfoMapper.selectList(new QueryWrapper<CartInfo>().eq("user_id", userId));
        // 判断这个集合
        if(CollectionUtils.isEmpty(cartInfoList)){
            return cartInfoList;
        }
        // 将数据放入缓存！
        String cartKey = getCartKey(userId);
        // hset(key,field,value)  hmset(key,map)
        HashMap<String, CartInfo> hashMap = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            //  redisTemplate.opsForHash().put(cartKey,cartInfo.getSkuId().toString(),cartInfo);
            // 细节问题： 数据库表cart_info 中没有skuPrice这个字段，那么我们为了将这个字段写入缓存时，不能为空
            // 同时，需要将最新的商品价格赋值给skuPrice.
            cartInfo.setSkuPrice(productFeignClient.getSkuPrice(cartInfo.getSkuId()));
            hashMap.put(cartInfo.getSkuId().toString(),cartInfo);
        }
        redisTemplate.opsForHash().putAll(cartKey,hashMap);
        // 设置过期时间
        setCartKeyExpire(cartKey);
        return cartInfoList;
    }

    // 设置购物车的过期时间
    private void setCartKeyExpire(String cartKey) {
        redisTemplate.expire(cartKey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }

    // 获取购物车的缓存key
    private String getCartKey(String userId){
        // key = user:userId:cart 谁的购物车
        return RedisConst.USER_KEY_PREFIX+userId+RedisConst.USER_CART_KEY_SUFFIX;
    }

}

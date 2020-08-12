package com.atguigu.gmall.order.controller;

import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author mqx
 * @date 2020-8-10 10:46:23
 */
@RestController
@RequestMapping("api/order")
public class OrderApiController {

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;


    // 编写一个控制器 ,如果要想看订单详情，则必须要登录，这个控制器结构要与网关相符
    // antPathMatcher.match("/api/**/auth/**",path)
    // 这个控制器返回的数据应该页面进行渲染！ ${detailArrayList} userAddressList,totalNum,totalAmount 页面需要的数据！
    // 商品详情时，我们也做过数据汇总
    @GetMapping("auth/trade")
    public Result<Map<String, Object>> trade(HttpServletRequest request){
        Map<String, Object> map = new HashMap<>();
        /*
        1.  通过userFeignClient 获取到用户收货地址列表
        2.  通过cartFeignClient 获取送货清单
        3.  还需要计算总金额
         */
        // 获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        // 获取用户收货地址列表
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);

        // 获取送货清单
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);

        // 声明一个订单明细集合
        List<OrderDetail> orderDetailList = new ArrayList<>();

        // int totalNum = 0;
        // 送货清单显示的orderDetail
        if (!CollectionUtils.isEmpty(cartCheckedList)){
            // 循环遍历，放入到OrderDetail集合中
            for (CartInfo cartInfo : cartCheckedList) {
                // 创建订单明细对象
                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setOrderPrice(cartInfo.getSkuPrice());
                orderDetail.setSkuNum(cartInfo.getSkuNum());
                orderDetail.setSkuName(cartInfo.getSkuName());
                orderDetail.setImgUrl(cartInfo.getImgUrl());
                orderDetail.setSkuId(cartInfo.getSkuId());
                // 将订单明细放入集合
                orderDetailList.add(orderDetail);
                // totalNum 一种:可以计算有几个spu中的sku ，另一种：计算几个spu中的skuId 的个数！
                // totalNum += cartInfo.getSkuNum();
            }
        }

        // 获取总金额： OrderInfo.sumTotalAmount();
        // 要声明一个OrderInfo 对象 并将订单明细集合赋值给orderDetailList
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailList);

        // 直接调用：sumTotalAmount(); 计算完成之后将结果给了orderInfo.totalAmount
        orderInfo.sumTotalAmount();

        // 获取流水号
        String tradeNo = orderService.getTradeNo(userId);

        // 保存数据
        // 页面需要 ：tradeNo: [[${tradeNo}]]
        map.put("tradeNo",tradeNo);
        map.put("detailArrayList",orderDetailList);
        map.put("userAddressList",userAddressList);
        // 第一种：有多少个spu下的sku
        map.put("totalNum",orderDetailList.size());
        // map.put("totalNum",totalNum);
        map.put("totalAmount",orderInfo.getTotalAmount());
        return Result.ok(map);
    }

    // 保存订单数据
    @PostMapping("auth/submitOrder")
    public Result saveOrderInfo(@RequestBody OrderInfo orderInfo,HttpServletRequest request){
        // 获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));
        // 判断是否是无刷新回退重复提交订单
        // /auth/trade/auth/submitOrder?tradeNo=tradeNo
        String tradeCode = request.getParameter("tradeNo");
        // 做比较
        Boolean flag = orderService.checkTradeNo(tradeCode, userId);
        // 判断
        //        if(flag){
        //            // true 正常
        //        }else {
        //            // 异常
        //        }
        // 异常
        if(!flag){
            return Result.fail().message("不能无刷新回退提交订单！");
        }
        // 声明一个字符串集合
        List<String> errorList = new ArrayList<>();
        // 声明一个CompletableFuture 集合
        List<CompletableFuture> completableFutureList = new ArrayList<>();
        // 验证库存：验证每个商品
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (!CollectionUtils.isEmpty(orderDetailList)){
            // 循环遍历验证每个商品
            for (OrderDetail orderDetail : orderDetailList) {
                // 验证库存runAsync() 没有返回值
                CompletableFuture<Void> checkStockCompletableFuture = CompletableFuture.runAsync(() -> {
                    boolean res = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                    if (!res) {
                        // 表示验证没有通过
                        errorList.add(orderDetail.getSkuName() + "库存不足！");
                    }
                },threadPoolExecutor);
                // 添加验证库存
                completableFutureList.add(checkStockCompletableFuture);

                // 验证价格
                CompletableFuture<Void> checkPriceCompletableFuture = CompletableFuture.runAsync(() -> {
                    //  获取商品的最新价格
                    BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                    // 写在这个位置！ 订单价格与商品的真是价格比较
                    if (orderDetail.getOrderPrice().compareTo(skuPrice) != 0) {
                        // 如果价格变化了，那么会影响购物车，我们需要将购物车中的数据变为最新的！
                        // 从数据库将最新价格查询一遍，放入缓存，给购物车，由购物车再到订单！
                        cartFeignClient.loadCartCache(userId);
                        // return Result.fail().message(orderDetail.getSkuName()+"价格有变动！");
                        errorList.add(orderDetail.getSkuName() + "价格有变动！");
                    }
                },threadPoolExecutor);
                // 添加价格验证
                completableFutureList.add(checkPriceCompletableFuture);
//                // 类似于商品详情
//                CompletableFuture.allOf(checkPriceCompletableFuture,
//                        checkStockCompletableFuture).join();
            }

            // 合并异步编排
            // 声明一个异步编排数组
            CompletableFuture[] completableFutures = new CompletableFuture[completableFutureList.size()];
            // 将异步编排的多个对象进行整合
            CompletableFuture.allOf(completableFutureList.toArray(completableFutures)).join();

            // 判断如果集合长度大于0 肯定由验证不通过的！
            if(errorList.size()>0){
                // return Result.fail().message(orderDetail.getSkuName()+"价格有变动！");
                // errorList 存储的都是字符串
                // join() 第一个参数表示要分割的元数据 第二个参数表示用什么来分割
                return Result.fail().message(StringUtils.join(errorList,","));
            }
        }

        // 比较完成之后删除缓存的流水号
        orderService.deleteTradeNo(userId);
        // 自定义的方法
        Long orderId = orderService.saveOrderInfo(orderInfo);
        // 通过 IService 接口调用的。
        // 需要在这个控制器中 设置一些页面没有传递过来的数据 ，总金额
        // orderService.save(orderInfo);
        return Result.ok(orderId);
    }

}

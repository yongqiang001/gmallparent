package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.activity.util.DateUtil;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/activity/seckill")
public class SeckillGoodsApiController {
    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private OrderFeignClient orderFeignClient;

    // 返回全部列表
    @GetMapping("/findAll")
    public Result findAll(){
        return Result.ok(seckillGoodsService.findAll());
    }

    // 获取实体类
    @GetMapping("/getSeckillGoods/{skuId}")
    public Result getSeckillGoods(@PathVariable Long skuId){
        return Result.ok(seckillGoodsService.getSeckillGoods(skuId));
    }

    // 获取下单码
    // 获取下单码： 为了防止用户直接跳过商品详情进入秒杀url！
    // 下单码：生成规则 将用户Id{userId},md5的一种加密作为下单码！
    @GetMapping("auth/getSeckillSkuIdStr/{skuId}")
    public Result getSeckillSkuIdStr(@PathVariable Long skuId, HttpServletRequest request){
        // auth/getSeckillSkuIdStr?skuId=xxx String skuId = request.getParameter("skuId");
        // 获取 userId
        String userId = AuthContextHolder.getUserId(request);
        // 什么时候才能让用户获取下单码?
        if (!StringUtils.isEmpty(userId)){
            // 活动开始之后, 结束之前
            SeckillGoods seckillGoods = seckillGoodsService.getSeckillGoods(skuId);
            // 获取当前系统时间
            Date curTime = new Date();
            // 应该获取下单码
            if (DateUtil.dateCompare(seckillGoods.getStartTime(),curTime) &&
                    DateUtil.dateCompare(curTime,seckillGoods.getEndTime())){
                // 使用MD5 工具类对userId 进行加密 生产下单码
                String skuIdStr = MD5.encrypt(userId);
                return Result.ok(skuIdStr);
            }
        }


        return Result.fail().message("获取下单码失败");
    }

    // url: this.api_name + '/auth/seckillOrder/{skuId} + '?skuIdStr=' + skuIdStr,
    @PostMapping("auth/seckillOrder/{skuId}")
    public Result seckillOrder(@PathVariable Long skuId, HttpServletRequest request){
         /*
        1.  验证下单码 利用md5 对userId 进行加密
        2.  验证状态码
        3.  将用户放入mq
         */
        // 获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        // 获取页面传递过来的下单码
        String skuIdStr = request.getParameter("skuIdStr");
        //判断
        if (!MD5.encrypt(userId).equals(skuIdStr)){
            // 提示不合法
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }
        // 验证状态码 存在内存中！
        // skuId:1 | skuId:0
        String state = (String) CacheHelper.get(skuId.toString());
        // 判断
        if(StringUtils.isEmpty(state)){
            // 提示不合法
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }

        // 继续判断
        if ("1".equals(state)){
            // 声明对象UserRecode
            UserRecode userRecode = new UserRecode();
            // 赋值：哪个用户购买哪个商品
            userRecode.setUserId(userId);
            userRecode.setSkuId(skuId);

            // 发送消息到mq 中，排队！
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_SECKILL_USER,MqConst.ROUTING_SECKILL_USER,userRecode);
        }else {
            // 已售罄
            return Result.build(null, ResultCodeEnum.SECKILL_FINISH);
        }
        // 返回
        return Result.ok();

    }
    // 查询秒杀状态
    @GetMapping(value = "auth/checkOrder/{skuId}")
    public Result checkOrder(@PathVariable Long skuId, HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);

        return seckillGoodsService.checkOrder(skuId, userId);
    }
    // 秒杀确认
    @GetMapping("auth/trade")
    public Result trade(HttpServletRequest request){
        // 获取userId
        String userId = AuthContextHolder.getUserId(request);
        // 先得到用户想要购买的商品！
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);

        if (null == orderRecode){
            return Result.fail().message("非法操作");
        }
        SeckillGoods seckillGoods = orderRecode.getSeckillGoods();

        // 获取用户地址
        List<UserAddress> userAddressList  = userFeignClient.findUserAddressListByUserId(userId);

        // 声明一个集合来存储订单明细
        ArrayList<OrderDetail> detailArrayList  = new ArrayList<>();
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setSkuId(seckillGoods.getSkuId());
        orderDetail.setSkuName(seckillGoods.getSkuName());
        orderDetail.setImgUrl(seckillGoods.getSkuDefaultImg());
        orderDetail.setSkuNum(orderRecode.getNum());
        orderDetail.setOrderPrice(seckillGoods.getCostPrice());
        // 添加到集合
        detailArrayList.add(orderDetail);

        // 计算总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(detailArrayList);
        orderInfo.sumTotalAmount();

        Map<String, Object> result = new HashMap<>();
        result.put("userAddressList", userAddressList);  // key  不能随意写, 是前端页面里面的
        result.put("detailArrayList", detailArrayList);
        // 保存总金额
        result.put("totalAmount", orderInfo.getTotalAmount());
        return Result.ok(result);

    }
    // 秒杀提交订单
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody  OrderInfo orderInfo, HttpServletRequest request ){
        // 获取userId
        String userId = AuthContextHolder.getUserId(request);
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);

        if (null == orderRecode) {
            return Result.fail().message("非法操作");
        }

        orderInfo.setUserId(Long.parseLong(userId));

        Long orderId = orderFeignClient.submitOrder(orderInfo);
        if (null == orderId) {
            return Result.fail().message("下单失败，请重新操作");
        }

        //删除下单信息
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(userId);
        //下单记录
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).put(userId, orderId.toString());

        return Result.ok(orderId);
    }

}

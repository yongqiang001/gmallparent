package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillGoodsServiceImpl implements SeckillGoodsService {
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    // 查询全部
    @Override
    public List<SeckillGoods> findAll() {
        List<SeckillGoods> seckillGoodsList  = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).values();
        return seckillGoodsList;
    }

    // 根据ID获取实体
    @Override
    public SeckillGoods getSeckillGoods(Long skuId) {
        return (SeckillGoods)redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(skuId.toString());
    }

    //创建订单
    @Override
    public void seckillOrder(Long skuId, String userId) {
        /*
            1.  验证状态码
            2.  判断用户是否已经下过订单
            3.  获取商品数据
            4.  完成预下单处理 OrderRecode
            5.  更新库存
         */
        //产品状态位， 1：可以秒杀 0：秒杀结束
        String state = (String) CacheHelper.get(skuId.toString());
        if ("0".equals(state)){
            //已售罄
            return;
        }
        // 判断用户是否已经下过订单 setnx();
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(RedisConst.SECKILL_USER + userId, skuId, RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        // 如果执行为true： 第一次下订单，如果是fasle: 说明用户已经购买过！
        if (!flag){
            // 用户已经下过订单
            return;
        }
        // 获取商品数据 将其存储redis-list
        // redisTemplate.opsForList().rightPop(RedisConst.SECKILL_STOCK_PREFIX + skuId);
        String goodIds = (String) redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).rightPop();
        // 判断商品是否已经售罄
        if(StringUtils.isEmpty(goodIds)){
            // 通知其他兄弟节点
            redisTemplate.convertAndSend("seckillpush", skuId + ":0");
            // 返回
            return;
        }
        // 完成预下单处理 OrderRecode
        OrderRecode orderRecode = new OrderRecode();
        orderRecode.setUserId(userId);
        orderRecode.setNum(1);
        orderRecode.setSeckillGoods(getSeckillGoods(skuId));
        // 订单码
        orderRecode.setOrderStr(MD5.encrypt(userId + skuId));  // 随意设置


        // 将预下单数据存储缓存
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(orderRecode.getUserId(), orderRecode);

        // 更新数据库：
        updateStockCount(orderRecode.getSeckillGoods().getSkuId());


    }

    // 根据商品id与用户ID查看订单信息
    @Override
    public Result checkOrder(Long skuId, String userId) {
        // 用户在缓存中存在，有机会秒杀到商品
        Boolean isExist =redisTemplate.hasKey(RedisConst.SECKILL_USER + userId);
        if (isExist) {
            //判断用户是否正在排队
            //判断用户是否下单
            Boolean isHasKey = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).hasKey(userId);
            if (isHasKey) {
                //抢单成功
                OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
                // 秒杀成功！
                return Result.build(orderRecode, ResultCodeEnum.SECKILL_SUCCESS);
            }
        }

        //判断是否下单
        Boolean isExistOrder = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).hasKey(userId);
        if(isExistOrder) {
            String orderId = (String)redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId);
            return Result.build(orderId, ResultCodeEnum.SECKILL_ORDER_SUCCESS);
        }

        String state = (String) CacheHelper.get(skuId.toString());
        if("0".equals(state)) {
            //已售罄 抢单失败
            return Result.build(null, ResultCodeEnum.SECKILL_FINISH);
        }

        //正在排队中
        return Result.build(null, ResultCodeEnum.SECKILL_RUN);
    }

    // 更新库存：
    private void updateStockCount(Long skuId) {
        // 库存剩余数量其实就是 redis - list 的长度
        Long count = redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).size();
        // 做数据库的库存更新 ，为了减轻服务器的压力 如果是2的倍数，则更新一次数据库。
        if (count % 2 == 0){
            // 数据库中有，还有缓存也有！
            SeckillGoods seckillGoods = getSeckillGoods(skuId);
            seckillGoods.setStockCount(count.intValue());
            // 更新数据库
            seckillGoodsMapper.updateById(seckillGoods);

            // 接着更新缓存
            redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);

        }
    }
}

package com.atguigu.gmall.activity.receiver;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.DateUtil;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Date;
import java.util.List;


@Component
public class SeckillReceiver {

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_1 , durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_TASK_1}
    ))
    public void importItemToRedis(Message message, Channel channel) {
          /*
        4.3.1	扫描数据库中是否有秒杀商品
                    什么是秒杀商品？
                    status=1
                    stock_count>0
                    start_time=new Date(); y-m-d
        4.3.2	将扫描到的商品放入到redis
                    hset(key,field,value); key = seckill:goods ,field = skuId, value = seckillGoods
        4.3.3	将秒杀商品的数量放入redis
                    list
        4.3.4	初始化状态位
                    暂无！
         */
        QueryWrapper<SeckillGoods> seckillGoodsQueryWrapper = new QueryWrapper<>();
        // 查询审核状态1 并且库存数量大于0,  当天的商品
        seckillGoodsQueryWrapper.eq("status", 1).gt("stock_count", 0);
        // y-m-d 需要格式化
        seckillGoodsQueryWrapper.eq("DATE_FORMAT(start_time,'%Y-%m-%d')", DateUtil.formatDate(new Date()));
        // 获取到当前秒杀的商品
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(seckillGoodsQueryWrapper);
        // 判断不为空
        if (!CollectionUtils.isEmpty(seckillGoodsList)) {
            // 遍历循环
            for (SeckillGoods seckillGoods : seckillGoodsList) {
                // 判断缓存中是否有这个key, 如果存在则不放数据, 用什么存储hash, String set(key, value)
                // RedisConst.SECKILL_GOODS  hset(key,field,value); key = seckill:goods ,field = skuId, value = seckillGoods
                Boolean flag = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).hasKey(seckillGoods.getSkuId().toString());
                // 如果true 说明缓存有数据
                if (flag) {
                    continue;
                }
                // 商品放入缓存！
                redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);
                // 将秒杀商品的数量放入redis list.
                //根据每一个商品的数量把商品按队列的形式放进redis中
                for (Integer i = 0; i < seckillGoods.getNum(); i++) {
                    // 商品数量放入list中! leftPush(); rightPop();
                    // key = seckill: stock: skuId
                    redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId())
                            .leftPush(seckillGoods.getSkuId().toString());
                }
                // 通知其他兄弟节点 skuId:1 初始化都是1
                redisTemplate.convertAndSend("seckillpush", seckillGoods.getSkuId() + ":1");
            }
            // 手动确认
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
    }

    // 监听队列中的用户
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_SECKILL_USER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_SECKILL_USER, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_SECKILL_USER}
    ))
    public void seckill(UserRecode userRecode, Message message, Channel channel) {
        // 数据判断
        if (null != userRecode) {
            // 说明有数据！ 开始处理预下单！
            seckillGoodsService.seckillOrder(userRecode.getSkuId(), userRecode.getUserId());
        }
        // 手动ack
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);


    }
    /**
     * 秒杀结束清空缓存
     *
     * @param message
     * @param channel
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_18, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_TASK_18}
    ))
    public void clearRedis(Message message, Channel channel) throws IOException {

        //活动结束清空缓存
        QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        queryWrapper.le("end_time", new Date());
        List<SeckillGoods> list = seckillGoodsMapper.selectList(queryWrapper);
        //清空缓存
        for (SeckillGoods seckillGoods : list) {
            redisTemplate.delete(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId());
        }
        redisTemplate.delete(RedisConst.SECKILL_GOODS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS_USERS);
        //将状态更新为结束
        SeckillGoods seckillGoodsUp = new SeckillGoods();
        seckillGoodsUp.setStatus("2");
        seckillGoodsMapper.update(seckillGoodsUp, queryWrapper);
        // 手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

}

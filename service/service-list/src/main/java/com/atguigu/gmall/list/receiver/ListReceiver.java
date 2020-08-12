package com.atguigu.gmall.list.receiver;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.list.service.SearchService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 接收后台系统传递过来的消息队列
@Component
public class ListReceiver {
    @Autowired
    private SearchService searchService;


    // 商品上架
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_GOODS_UPPER, autoDelete = "false", durable = "ture"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_GOODS, autoDelete = "false", durable = "ture"),
            key = {MqConst.ROUTING_GOODS_UPPER}
    ))

    public void upperGoods(Long skuId, Message message, Channel channel) throws IOException {
        if (null != skuId){
            // 调用商品的上架
            searchService.upperGoods(skuId);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

    }

    // 商品下架
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_GOODS_LOWER, autoDelete = "false", durable = "ture"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_GOODS, autoDelete = "false", durable = "ture"),
            key = {MqConst.ROUTING_GOODS_LOWER}
    ))

    public void lowerGoods(Long skuId, Message message, Channel channel) throws IOException {
        if (null != skuId){
            // 调用商品的上架
            searchService.lowerGoods(skuId);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

    }

}

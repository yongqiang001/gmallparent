package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author mqx
 * 处理过期订单
 * @date 2020-8-13 10:52:41
 */
@Component
public class OrderReceiver {
    // 本质就是更新状态
    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @Autowired
    private RabbitService rabbitService;

    // 设置监听消息 ，发送的消息orderId
    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message , Channel channel){
        // 获取到发送过来的消息
        if (orderId!=null){
            // 根据这个订单Id 查询订单表中是否有该笔订单。
            // select * from order_info where id = orderId
            OrderInfo orderInfo = orderService.getById(orderId);
            // 有这笔订单 并且 订单状态和进程状态都是未支付的情况下，才能关闭过期订单
            // 因为订单的进度中能够获取到订单的状态
            if (orderInfo!=null && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){
                // 更新订单状态变已关闭
                // orderService.execExpiredOrder(orderInfo.getId());
                // 判断是否有交易记录
                PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                // 如果交易记录不为空，并且支付状态是未支付！
                if (paymentInfo!=null && paymentInfo.getPaymentStatus().equals(PaymentStatus.UNPAID.name())){
                    // 准备关闭交易记录{paymentInfo , aliPay }
                    // 在支付宝中是否有交易记录
                    Boolean flag = paymentFeignClient.checkPayment(orderId);
                    if (flag){
                        // 在支付宝中有交易记录，那么调用关闭支付宝的方法。
                        Boolean reslut = paymentFeignClient.closePay(orderId);
                        // 判断是否关闭成功！
                        if (reslut){
                            // 支付宝中交易关闭成功，同时关闭orderInfo,paymentInfo
                            orderService.execExpiredOrder(orderInfo.getId(),"2");
                        }else{
                            // 关闭失败，说明用户支付了，发送消息队列走正常流程！
                            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,
                                    orderId);
                        }
                    }else {
                        // 关闭orderInfo,paymenInfo
                        orderService.execExpiredOrder(orderInfo.getId(),"2");
                    }

                }else {
                    // 说明没有交易记录，但是有订单数据，关闭过期订单、
                    orderService.execExpiredOrder(orderInfo.getId(),"1");
                }
            }
        }
        // 手动ack
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

    // 接收消息
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY,durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void paySuccess(Long orderId, Message message , Channel channel){
        // 获取到发送过来的消息
        if (orderId!=null){
            // 根据这个订单Id 查询订单表中是否有该笔订单。
            // select * from order_info where id = orderId
            OrderInfo orderInfo = orderService.getById(orderId);
            // 有这笔订单 并且 订单状态和进程状态都是未支付的情况下，才能关闭过期订单
            // 因为订单的进度中能够获取到订单的状态
            if (orderInfo!=null && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){
                // 更新订单状态为已支付
                orderService.updateOrderStatus(orderId,ProcessStatus.PAID);
                // 发送消息通知仓库系统，减库存！
                orderService.sendOrderStatus(orderId);
            }
        }
        // 手动ack
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

    // 接收库存系统发过来的消息！
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_WARE_ORDER,durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_WARE_ORDER),
            key = {MqConst.ROUTING_WARE_ORDER}
    ))
    public void updateOrderStatus(String msgJson, Message message , Channel channel){
        if (!StringUtils.isEmpty(msgJson)){
            Map map = JSON.parseObject(msgJson, Map.class);
            // 获取对应的数据
            String orderId = (String) map.get("orderId");
            String status = (String) map.get("status");
            if ("DEDUCTED".equals(status)){
                // 减库存成功！
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.WAITING_DELEVER);
            }else {
                // 减库存失败！超卖
                //  远程调用其他仓库商品！ 人工客服！
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.STOCK_EXCEPTION);
            }

        }
        // 手动ack
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}

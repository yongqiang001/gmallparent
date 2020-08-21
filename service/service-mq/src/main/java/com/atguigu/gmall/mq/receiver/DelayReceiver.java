package com.atguigu.gmall.mq.receiver;

import com.atguigu.gmall.mq.config.DelayedMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
@Configuration
public class DelayReceiver {
    // 监听消息
    @RabbitListener(queues = DelayedMqConfig.queue_delay_1)
    public void get(String msg) {
        System.out.println("接收到的消息:" + msg);
        // 发送消息, 在队列中设置一个过期时间 10 秒
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println(  sdf.format(new Date()) + "接收到消息了---------" + msg);
    }

}
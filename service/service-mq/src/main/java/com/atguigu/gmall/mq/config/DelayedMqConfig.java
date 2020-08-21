package com.atguigu.gmall.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;


@Configuration
public class DelayedMqConfig {
    // 声明一些变量  只有一个交换机, 一个队列, 一个路由键
    public static final String exchange_delay = "exchange.delay";
    public static final String routing_delay = "routing.delay";
    public static final String queue_delay_1 = "queue.delay.1";

    // 声明一个交换机
    @Bean
    public CustomExchange delayExchange(){

        HashMap<String, Object> args = new HashMap<>();
        // 设置一个参数
        args.put("x-delayed-type", "direct");
        // 返回
        return new CustomExchange(exchange_delay, "x-delayed-message", true, false, args);
    }
    // 声明一个队列
    @Bean
    public Queue delayQeue1(){
        // 返回队列
        return new Queue(queue_delay_1, true, false,false );
    }

    @Bean
    public Binding delayBbinding1(){
        return BindingBuilder.bind(delayQeue1()).to(delayExchange()).with(routing_delay).noargs();
    }
}

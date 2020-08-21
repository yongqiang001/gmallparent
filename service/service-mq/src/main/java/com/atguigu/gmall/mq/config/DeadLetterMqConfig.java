package com.atguigu.gmall.mq.config;

import jdk.nashorn.internal.runtime.regexp.joni.constants.Arguments;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class DeadLetterMqConfig {

    // 声明一些变量
    public static final String exchange_dead = "exchange.dead";
    public static final String routing_dead_1 = "routing.dead.1";
    public static final String routing_dead_2 = "routing.dead.2";
    public static final String queue_dead_1 = "queue.dead.1";
    public static final String queue_dead_2 = "queue.dead.2";


    // 定义交换机
    @Bean   // 将创建好的交换机放入spring容器
    public DirectExchange exchange(){
        // 返回一个交换机
        // 第一个交换机名称, 第二个是否持久化, 第三个是否自动删除, 第四个是否有其他参数传入
        return new DirectExchange(exchange_dead, true, false, null);
    }
    // 设置第一个队列
    @Bean  //
    public Queue queue1(){

        // 声明一个arguments  满足三个条件之一的话, 那么交换机才能变为死信交换机. 采用消息的TTL
        HashMap<String, Object> arguments = new HashMap<>();
        // 设置key 和 value, 这个 arguments key是固定值.不能随意写
        arguments.put("x-dead-letter-exchange", exchange_dead);
        arguments.put("x-dead-letter-routing-key", routing_dead_2);
        // 设置延迟时间(存活时间)
        arguments.put("x-message-ttl", 10 * 1000);
        // 第一个队列的名称, 第二个是否持久化, 第三个表示是否独享, 排外的, 第四个是否自动删除
        return new Queue(queue_dead_1, true, false, false, arguments);
    }
    @Bean
    public Binding binding(){
        // 将队列一 通过routing_dead_1 key 绑定到exchange_dead 交换机上
        return BindingBuilder.bind(queue1()).to(exchange()).with(routing_dead_1);

    }
    // 这个队列二就是一个普通队列
    @Bean
    public Queue queue2(){
        return new Queue(queue_dead_2, true, false, false, null);
    }
    // 设置队列二的绑定规则
    @Bean
    public Binding binding2(){
        // 将队列二通过routing_dead_2 key 绑定到exchange_dead交换机上！
        return BindingBuilder.bind(queue2()).to(exchange()).with(routing_dead_2);
    }
}

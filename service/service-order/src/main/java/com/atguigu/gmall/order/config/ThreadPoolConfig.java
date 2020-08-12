package com.atguigu.gmall.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {
    // 创建线程池 放入spring 容器中！
    @Bean
    public ThreadPoolExecutor threadPoolExecutor(){
        return new ThreadPoolExecutor(
                10,
                50,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100)
                // 线程池工厂，拒绝策略
        );
    }
}

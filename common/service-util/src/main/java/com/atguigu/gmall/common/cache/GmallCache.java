package com.atguigu.gmall.common.cache;


import java.lang.annotation.*;

@Target(ElementType.METHOD) // 用在什么地方
@Retention(RetentionPolicy.RUNTIME)// 定义注解的声明周期
public @interface GmallCache {

    // 定义一个前缀 目的：用来区分是哪个方法的缓存
    String prefix() default "cache";
}
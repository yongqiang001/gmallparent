package com.atguigu.gmall.common.cache;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import springfox.documentation.spring.web.json.Json;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Component
@Aspect
public class GmallCacheAspect {

    // 这个类，将数据放入缓存。{走完整的分布锁}
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    // 利用环绕通知来获取对应的数据，做业务处理
    // GmallCache 将数据放入缓存。数据类型无法确定，所以给Object
    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
    public Object cacheAroundAdvice(ProceedingJoinPoint point){
        // 声明一个变量
        Object object = null;
        // 如何知道这个方法上，是否有该注解，包括注解的前缀是什么？
        // 先获取方法上的签名[MethodSignature]
        MethodSignature signature = (MethodSignature) point.getSignature();
        // 获取方法上的注解
        GmallCache gmallCache = signature.getMethod().getAnnotation(GmallCache.class);
        // 获取注解的前缀  sku:
        String prefix = gmallCache.prefix();
        // 模拟场景访问skuId 35 ，36, 37  在缓存中应该如何存储 key= sku:35 value =skuInfo.toString()
        // key= sku:36 key= sku:37
        // 获取请求的参数 35,36,27
        Object[] args = point.getArgs();
        // 制作一个缓存的key
        String key = prefix+ Arrays.asList(args).toString();

        // 通过key 来获取缓存中的数据，
        object = cacheHit(key);

        // 如果缓存中数据为空！
        if (object==null){
            try {
                // 添加分布式锁，从数据库获取数据，并放入缓存
                // 使用redisson 做分布式锁
                RLock lock = redissonClient.getLock(key + ":lock");
                // 判断是否加锁成功！
                boolean flag = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
                if (flag){
                    try {
                        // 如果是true 表示上锁成功！获取数据库中的数据。
                        object = point.proceed(point.getArgs()); // 表示执行带有GmallCache注解的方法体,并返回数据
                        // 判断你的object 是否为空！说明数据库没有数据。防止缓存穿透
                        if (object==null){
                            // 设置一个空对象进去,这个数据应该给一个短暂的过期时间
                            Object object1 = new Object();
                            redisTemplate.opsForValue().set(key,JSON.toJSONString(object1),RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                            // 返回不存在的对象
                            return object1;
                        }
                        // 如果查询出来数据不为空！
                        redisTemplate.opsForValue().set(key,object,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                        // 返回数据
                        return object;
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    } finally {
                        lock.unlock();
                    }
                }else {
                    // 睡眠，睡醒之后，从缓存获取数据
                    Thread.sleep(1000);
                    return cacheHit(key);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }else {
            return object;
        }
        return object;
    }

    private Object cacheHit(String key) {
        // skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
        Object cache = redisTemplate.opsForValue().get(key);
        // 从缓存获取数据，获取完数据之后，我们需要将数据返回，给页面渲染对吧！
        if (!StringUtils.isEmpty(String.valueOf(cache))){
            // 缓存中有数据，返回的是执行方法时对应的返回类型
            return cache;
        }
        return null;
    }
}
package com.atguigu.gmall.product.service.impl.impl;

import com.atguigu.gmall.product.service.TestService;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TestServiceImpl implements TestService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

//    @Override
//    public synchronized void testLock() {
//        // 查询redis中的num值
//        String value = (String)this.redisTemplate.opsForValue().get("num");
//        // 没有该值return
//        if (StringUtils.isBlank(value)){
//            return ;
//        }
//        // 有值就转成成int
//        int num = Integer.parseInt(value);
//        // 把redis中的num值+1
//        this.redisTemplate.opsForValue().set("num", String.valueOf(++num));
//    }
    @Override
    public void testLock(){
        // 查询redis中的num值
        String skuId = "27";
        // 设置锁的key
        String lockKey = "lock:" + skuId;
        RLock rLock = redissonClient.getLock(lockKey);
        // 加锁
        rLock.lock();

    }


}

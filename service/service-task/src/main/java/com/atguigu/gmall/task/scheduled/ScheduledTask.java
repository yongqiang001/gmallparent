package com.atguigu.gmall.task.scheduled;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
public class ScheduledTask {
    @Autowired
    private RabbitService rabbitService;

    // 分，时，日，月，周，年
    @Scheduled(cron = "1/15 * * * * ?")
    public void task1() {
        // 发送的消息
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK,  MqConst.ROUTING_TASK_1, "");
        // System.out.println("来人了，开始做饭吧！");
    }

    /**
     * 每天下午18点执行
     */
//@Scheduled(cron = "0/35 * * * * ?")
    @Scheduled(cron = "0 0 18 * * ?")
    public void task18() {

        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK, MqConst.ROUTING_TASK_18, "");
    }
}

package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;
/**
 * @author mqx
 * 后续会调用一些通用方法，IService
 * @date 2020-8-10 14:07:41
 */
public interface OrderService extends IService<OrderInfo> {


    // 保存订单信息
    Long saveOrderInfo(OrderInfo orderInfo);

    // 生产流水号接口
    String getTradeNo(String userId);

    // 比较流水号
    Boolean checkTradeNo(String tradeCode,String userId);

    // 删除流水号
    void deleteTradeNo(String userId);

    // 验证库存接口
    boolean checkStock(Long skuId, Integer skuNum);
}

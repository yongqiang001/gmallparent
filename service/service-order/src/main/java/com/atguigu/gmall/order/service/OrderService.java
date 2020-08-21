package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

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

    //处理过期订单
    void execExpiredOrder(Long orderId);


    /**
     * 根据订单Id 查询订单信息
     * @param orderId
     * @return
     */
    OrderInfo getOrderInfo(Long orderId);

    void updateOrderStatus(Long orderId, ProcessStatus paid);

    void sendOrderStatus(Long orderId);


    /**
     * 拆单方法
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    List<OrderInfo> orderSplit(Long orderId, String wareSkuMap);


    /**
     * 将orderInfo 中部分字段转换为Map集合
     * @param orderInfo
     * @return
     */
    Map initWareOrder(OrderInfo orderInfo);


    /**
     * 关闭过期订单方法
     * @param orderId
     * @param flag
     */
    void execExpiredOrder(Long orderId, String flag);
}

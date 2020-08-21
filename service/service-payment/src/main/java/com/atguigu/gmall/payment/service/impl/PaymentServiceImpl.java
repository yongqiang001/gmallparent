package com.atguigu.gmall.payment.service.impl;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.mapper.PaymentMapper;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private RabbitService rabbitService;

    // 保存交易记录
    @Override
    public void savePaymentInfo(OrderInfo orderInfo, String paymentType) {
        // 细节处理：paymentInfo 表中： 订单Id 和 支付方式 这个两个作为一个标准，不能重复。
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("order_id",orderInfo.getId());
        paymentInfoQueryWrapper.eq("payment_type",paymentType);
        Integer count = paymentMapper.selectCount(paymentInfoQueryWrapper);
        if (count>0){
            return;
        }
        // 创建一个paymentInfo 对象
        PaymentInfo paymentInfo = new PaymentInfo();
        // 给paymentInfo 赋值
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setOrderId(orderInfo.getId());
        paymentInfo.setPaymentType(paymentType);
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfo.setSubject(orderInfo.getTradeBody());
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID.name());
        paymentInfo.setCreateTime(new Date());

        paymentMapper.insert(paymentInfo);
    }
    //获取交易记录信息
    @Override
    public PaymentInfo getPaymentInfo(String outTradeNo, String name) {

            QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
            paymentInfoQueryWrapper.eq("out_trade_no",outTradeNo).eq("payment_type",name);
            PaymentInfo paymentInfo = paymentMapper.selectOne(paymentInfoQueryWrapper);
            return paymentInfo;

        }

    //支付成功
    @Override
    public void paySuccess(String outTradeNo, String name, Map<String, String> paramMap) {

        // 获取PaymentInfo对象
        PaymentInfo paymentInfoQuery = getPaymentInfo(outTradeNo, name);
        // 判断查询到的数据支付状态
        if (paymentInfoQuery.getPaymentStatus().equals(PaymentStatus.PAID.name()) ||
        paymentInfoQuery.getPaymentStatus().equals(PaymentStatus.ClOSED.name())){
            // 此时需要 return
            return;
        }
        // 设置更新的内容

        PaymentInfo paymentInfoUpd = new PaymentInfo();
        // update paymentInfo set PaymentStatus = PaymentStatus.PAID ,CallbackTime = new Date() where out_trade_no = ?
        paymentInfoUpd.setPaymentStatus(PaymentStatus.PAID.name());
        paymentInfoUpd.setCallbackTime(new Date());
        paymentInfoUpd.setTradeNo(paramMap.get("trade_no"));
        // 调用更新方法
        this.updatePaymentInfo(outTradeNo,paymentInfoUpd);
        // 表示交易成功！

        // 发送一个消息: 告诉订单, 并修改订单的状态!  异步操作
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,
                paymentInfoQuery.getOrderId());
    }


    // 根据第三方交易编号，修改支付交易记录
    // 单独提出来做一个可以重用的方法
    @Override
    public void updatePaymentInfo(String outTradeNo, PaymentInfo paymentInfoUpd) {
        QueryWrapper<PaymentInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("out_trade_no", outTradeNo);
        paymentMapper.update(paymentInfoUpd,queryWrapper);
    }

    @Override
    public void closePayment(Long orderId) {

        // 设置关闭交易记录的条件  118
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("order_id",orderId);
        // 如果当前的交易记录不存在，则不更新交易记录
        Integer count = paymentMapper.selectCount(paymentInfoQueryWrapper);
        if (null == count || count.intValue() == 0 ){
            return;
        }
        // 在关闭支付宝交易之前。还需要关闭paymentInfo
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
        paymentMapper.update(paymentInfo, paymentInfoQueryWrapper);

    }
}

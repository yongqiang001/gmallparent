package com.atguigu.gmall.payment.service;

import com.alipay.api.AlipayApiException;

public interface AlipayService {
    String createaliPay(Long orderId) throws AlipayApiException;

    // 退款
    Boolean refund(Long orderId);

    // 关闭交易
    Boolean closePay(Long orderId);

    //根据订单查询是否支付成功！
    Boolean checkPayment(Long orderId);
}

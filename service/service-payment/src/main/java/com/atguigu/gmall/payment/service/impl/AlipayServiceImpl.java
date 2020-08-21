package com.atguigu.gmall.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class AlipayServiceImpl implements AlipayService {
    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private AlipayClient alipayClient;

    @Override
    public String createaliPay(Long orderId) throws AlipayApiException {
        // out_trade_no
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);

        // 调用一个方法：保存交易记录方法。
        paymentService.savePaymentInfo(orderInfo, PaymentType.ALIPAY.name());

        // 生成二维码
        // AlipayClient alipayClient =  new DefaultAlipayClient( "https://openapi.alipay.com/gateway.do" , APP_ID, APP_PRIVATE_KEY, FORMAT, CHARSET, ALIPAY_PUBLIC_KEY, SIGN_TYPE);  //获得初始化的AlipayClient
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest(); //创建API对应的request
        // 设置同步回调地址
        // https://www.domain.com/CallBack/return_url?out_trade_no=ATGUIGU1597305841470556&version=1.0&app_id=2021001163617452&charset=utf-8&sign_type=RSA2&trade_no=2020081322001407381447186176&auth_app_id=2021001163617452&timestamp=2020-08-13%2016:06:00&seller_id=2088831489324244&method=alipay.trade.page.pay.return&total_amount=0.01&sign=SaF//jbFXYfzqOPRWOPtI62UYHslQxbjGTXVCIeupAS1XiNUyAjvnGnXRL75sJLkgGWyjJUjIlyX+liN4s1+xebK5lNw8JaQyuFw6hvecnQ/0kwMtxlw5nIzyn0KGQ2n58uFB24G2aZ+D88Tj7tvo++UhoJ1BHNzWUZySbrkplZhExQAU4yoiFDrLBQR63y6JtcFPa+GSihoYojB388o42aeVeje89mvWOPcHuiRdx/tey/5yCJPoVJ8dsdWtIJCbPYEpQLK/B4r4w6IIfrkow0iOEMsswS4FrdJNGRoH9BN7SexA5AnHuDVXO+5jMot4l8Hzkarygeat4AH1uAVvA==
        // alipayRequest.setReturnUrl( "http://domain.com/CallBack/return_url.jsp");
        // http://api.gmall.com/api/payment/alipay/callback/return
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        // 设置异步回调地址
        // alipayRequest.setNotifyUrl( "http://domain.com/CallBack/notify_url.jsp"); //在公共参数中设置回跳和通知地址
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);
        // 设置传入的参数 json 字符串。
        // 声明一个map 集合
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no", orderInfo.getOutTradeNo());
        map.put("product_code", "FAST_INSTANT_TRADE_PAY");
        map.put("total_amount", orderInfo.getTotalAmount());
        map.put("subject", "太热了，买个空调凉快凉快！");
        // 将map 转换为json 字符串传入！
        alipayRequest.setBizContent(JSON.toJSONString(map));
        // 返回页面要输出的内容
        return alipayClient.pageExecute(alipayRequest).getBody();  //调用SDK生成表单
    }

    @Override
    public Boolean refund(Long orderId) {
// 根据退款接口：(out_trade_no || trade_no) && refund_amount
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

        // out_trade_no ,在订单表，交易记录表都有，并这两个是同一个值！
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);

        // 创建一个map
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no", orderInfo.getOutTradeNo());
        map.put("refund_amount", orderInfo.getTotalAmount());
        map.put("refund_reason", "空调不够凉");

        request.setBizContent(JSON.toJSONString(map));
        AlipayTradeRefundResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if (response.isSuccess()) {
            // 说退款成功了, 需要关闭支付宝的交易状态！同时还需要关闭交易记录。
            System.out.println("调用成功");
            // 先关闭交易记录
            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setPaymentStatus(ProcessStatus.CLOSED.name());
            paymentService.updatePaymentInfo(orderInfo.getOutTradeNo(), paymentInfo);

            // 如果支付宝更新了交易状态，就不能继续退款了！
            return true;
        } else {
            System.out.println("调用失败");
            return false;
        }
    }


    @SneakyThrows
    @Override
    public Boolean closePay(Long orderId) {
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        // PaymentInfo paymentInfo = paymentService.getPaymentInfo(orderInfo.getOutTradeNo(), PaymentType.ALIPAY.name());
        // 引入aliPayClient
        // AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do","app_id","your private_key","json","GBK","alipay_public_key","RSA2");
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        HashMap<String, Object> map = new HashMap<>();
        // map.put("trade_no",paymentInfo.getTradeNo()); // 从paymentInfo 中获取！
        map.put("out_trade_no", orderInfo.getOutTradeNo());
        map.put("operator_id", "YX01");
        request.setBizContent(JSON.toJSONString(map));

        AlipayTradeCloseResponse response = alipayClient.execute(request);
        if (response.isSuccess()) {
            System.out.println("调用成功");
            return true;
        } else {
            System.out.println("调用失败");
            return false;
        }
    }

    @SneakyThrows
    @Override
    public Boolean checkPayment(Long orderId) {
        // 根据订单Id 查询订单信息
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no", orderInfo.getOutTradeNo());
        // 根据out_trade_no 查询交易记录
        request.setBizContent(JSON.toJSONString(map));
        AlipayTradeQueryResponse response = alipayClient.execute(request);
        return response.isSuccess();
    }
}
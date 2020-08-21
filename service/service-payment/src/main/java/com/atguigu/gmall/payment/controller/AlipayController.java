package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @author mqx
 * http://api.gmall.com/api/payment/alipay/submit/197
 * @date 2020-8-13 15:57:52
 */
@Controller
@RequestMapping("/api/payment/alipay")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private PaymentService paymentService;
    // 后面会有很多控制器，但是剩下的控制不需要返回Json 格式。
    @RequestMapping("submit/{orderId}")
    @ResponseBody // 1.表示将数转换为json 格式，2.还能将数据直接输入到页面
    public String aliPay(@PathVariable Long orderId){
        String from = "";
        try {
            from = alipayService.createaliPay(orderId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 返回数据
        return from;
    }

    // http://api.gmall.com/api/payment/alipay/callback/return
    // 完成同步回调
    @GetMapping("callback/return")
    public String callBackAlipay(){
        // 给用户看到支付成功页面
        // return_order_url=http://payment.gmall.com/pay/success.html
        return "redirect:"+ AlipayConfig.return_order_url;
    }

    // http://up5kdt.natappfree.cc/api/payment/alipay/callback/notify
    // 异步处理：
    // @RequestMapping
    @PostMapping("callback/notify")
    @ResponseBody
    public String callBackAlipayNotify(@RequestParam Map<String,String> paramMap){
        System.out.println("回来了！");

        boolean signVerified = false; //调用SDK验证签名
        try {
            signVerified = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        // 交易状态
        String trade_status = paramMap.get("trade_status");
        String out_trade_no = paramMap.get("out_trade_no");
        // true
        if (signVerified) {
            // TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            if ("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)) {
                // 但是，如果交易记录表中 PAID 或者 CLOSE  获取交易记录中的支付状态 通过outTradeNo来查询数据
                // select * from paymentInfo where out_trade_no=?
                PaymentInfo paymentInfo = paymentService.getPaymentInfo(out_trade_no, PaymentType.ALIPAY.name());
                if (paymentInfo.getPaymentStatus().equals(PaymentStatus.PAID.name()) || paymentInfo.getPaymentStatus().equals(PaymentStatus.ClOSED.name())){
                    return "failure";
                }

                // 正常的支付成功，我们应该更新交易记录状态
                paymentService.paySuccess(out_trade_no,PaymentType.ALIPAY.name(), paramMap);
                return "success";
            }

        } else {
            // TODO 验签失败则记录异常日志，并在response中返回failure.
            return "failure";
        }
        return "failure";
    }

    // 发起退款：http://localhost:8205/api/payment/alipay/refund/198
    @RequestMapping("refund/{orderId}")
    @ResponseBody
    public Result refund(@PathVariable Long orderId){

        // 调用服务层接口
        boolean flag = alipayService.refund(orderId);
        return Result.ok(flag);
    }

    // 关闭交易接口
    // localhost:8205/api/payment/alipay/closePay/207
    @GetMapping("closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId){
        // 方法调用
        Boolean flag = alipayService.closePay(orderId);
        // 返回数据
        return flag;
    }

    // 查看是否有交易记录
    // localhost:8205/api/payment/alipay/checkPayment/210
    @RequestMapping("checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId){
        // 方法调用
        Boolean flag = alipayService.checkPayment(orderId);
        // 返回数据
        return flag ;
    }

    // 查询paymentInfo数据接口
    @GetMapping("getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo){
        // 查询数据paymentInfo
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        return paymentInfo;
    }

}

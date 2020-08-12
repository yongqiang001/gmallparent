package com.atguigu.gmall.order.service.impl;

import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {
    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${ware.url}")
    private String wareUrl; // 获取到仓库地址


    @Override
    @Transactional
    public Long saveOrderInfo(OrderInfo orderInfo) {
        // 这个方法 特殊的设置一些页面没有传递过来的数据 ，
        // total_amount,order_status，userId,out_trade_no,trade_body,create_time,expire_time,process_status,[tracking_no,parent_order_id{拆单},img_url]
        // 计算总金额 有orderInfo ,并且有 orderDetailList 属性有值 ，可以直接调用方法sumTotalAmount()
        orderInfo.sumTotalAmount();
        // 设置订单状态
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        // userId 暂时获取不到！但是，可以在控制器获取

        // 赋值第三方交易编号
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        // 订单主体
        // 方法一： orderInfo.setTradeBody("结婚买车");
        // 方法二：可以拼接商品的名称
        // 遍历订单明细
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        // 定义一个变量记录
        StringBuilder tradeBody = new StringBuilder();
        // 由项目经理：
        for (OrderDetail orderDetail : orderDetailList) {
            // 获取商品的名称进行拼接
            tradeBody.append(orderDetail.getSkuName());
        }
        // 注意下这个长度
        if (tradeBody.toString().length()>200){
            String tradeBodyAfter = tradeBody.toString().substring(0, 200);
            orderInfo.setTradeBody(tradeBodyAfter);
        }else {
            orderInfo.setTradeBody(tradeBody.toString());
        }
        // 创建时间
        orderInfo.setCreateTime(new Date());
        // 过期时间 默认设置24小时 ， 比如说笔记本库存非常充足的24 ，如果不充足的，那么给30分钟。
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);
        orderInfo.setExpireTime(calendar.getTime());
        // 订单的进程状态 {包含订单状态}
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());

        // 保存到数据库
        orderInfoMapper.insert(orderInfo);

        // 保存订单明细
        List<OrderDetail> orderDetailsList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailsList) {
            // 赋值orderId
            // orderInfo插入的时候，没有给过id ，那么我们在这用会不会报空指针？
            // 不会： 执行完成插入之后，OrderInfo 实体类有个id @TableId(type = IdType.AUTO)
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insert(orderDetail);
        }
        // 返回订单Id
        return orderInfo.getId();
    }

    @Override
    public String getTradeNo(String userId) {
        // 生成的流水号
        String tradeNo = UUID.randomUUID().toString();
        // 保存到缓存中
        // 定义key
        String tradeNoKey = "user:"+userId+":tradeNo";
        // 放入缓存
        redisTemplate.opsForValue().set(tradeNoKey,tradeNo);
        // 返回流水号
        return tradeNo;
    }

    //  页面提交过来的流水号
    @Override
    public Boolean checkTradeNo(String tradeCode, String userId) {
        // 需要页面提交的，还有缓存的
        // 定义key
        String tradeNoKey = "user:"+userId+":tradeNo";
        String tradeNo = (String) redisTemplate.opsForValue().get(tradeNoKey);
        // 返回比较结果
        return tradeCode.equals(tradeNo);
    }

    @Override
    public void deleteTradeNo(String userId) {
        // 定义key
        String tradeNoKey = "user:"+userId+":tradeNo";
        // 删除流水号
        redisTemplate.delete(tradeNoKey);
    }

    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        // http://localhost:9001/hasStock?skuId=10221&num=2
        // ware-manage 是独立的springboot项目. 使用httpclient 调用
        String result = HttpClientUtil.doGet(wareUrl + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        // 做返回值设置  0：无库存   1：有库存
        return "1".equals(result);
    }

}

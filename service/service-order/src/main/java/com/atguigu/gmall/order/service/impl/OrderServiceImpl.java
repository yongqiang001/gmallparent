package com.atguigu.gmall.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
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

    @Autowired
    private RabbitService rabbitService;

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


        // 在此应该发送一个延迟队列: 为了测试给了2分钟时间
        //发送延迟队列，如果定时未支付，取消订单
        rabbitService.sendDelayMessage(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL, MqConst.ROUTING_ORDER_CANCEL, orderInfo.getId(), MqConst.DELAY_TIME);

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

    // 处理过期订单
    @Override
    public void execExpiredOrder(Long orderId) {
        // orderInfo
        updateOrderStatus(orderId, ProcessStatus.CLOSED);
        // paymentInfo
        //paymentFeignClient.closePayment(orderId);
        //取消交易
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE, MqConst.ROUTING_PAYMENT_CLOSE, orderId);
    }

    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        QueryWrapper<OrderDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id", orderId);
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(queryWrapper);

        orderInfo.setOrderDetailList(orderDetailList);
        return orderInfo;
    }

    public void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(processStatus.name());
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfoMapper.updateById(orderInfo);
    }

    @Override
    public void sendOrderStatus(Long orderId) {
        // 将订单的状态改为已通知仓库
        updateOrderStatus(orderId,ProcessStatus.NOTIFIED_WARE);
        // 要发送的字符串
        String wareJson = initWareOrder(orderId);
        // 发送通知给库存
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK,MqConst.ROUTING_WARE_STOCK,wareJson);
    }

    @Override
    public List<OrderInfo> orderSplit(Long orderId, String wareSkuMap) {
        // 子订单明细集合
        List<OrderInfo> subOrderInfoList = new ArrayList<>();
        /*
           1.   先获取原始订单
           2.   将wareSkuMap 参数转换为我们可以操作的对象！
            [{"wareId":"1","skuIds":["2","10"]},
            {"wareId":"2","skuIds":["3"]}]
           3.   创建子订单
           4.   给子订单赋值{orderInfo，orderDetail}
           5.   保存数据
           6.   把子订单放入集合
           7.   修改订单状态！
         */
        OrderInfo orderInfoOrigin  = getOrderInfo(orderId);
        // 参数数据转换
        List<Map> mapList = JSON.parseArray(wareSkuMap, Map.class);
        // 循环遍历数据
        for (Map map : mapList) {
            // 获取map中的数据
            String wareId = (String) map.get("wareId");
            List<String> skuIds = (List<String>) map.get("skuIds");
            // new 子订单
            OrderInfo subOrderInfo = new OrderInfo();
            BeanUtils.copyProperties(orderInfoOrigin,subOrderInfo);
            // 订单编号设置为null ，自动增长
            subOrderInfo.setId(null);
            // 父Id
            subOrderInfo.setParentOrderId(orderId);
            // 赋值仓库Id
            subOrderInfo.setWareId(wareId);
            // 金额需要根据订单明细。
            // 获取原始订单中的订单明细
            List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
            // 声明一个子订单明细集合
            List<OrderDetail> orderDetails = new ArrayList<>();

            // 循环遍历：
            for (OrderDetail orderDetail : orderDetailList) {
                // orderDetail.getSkuId() 与 仓库Id 所对应的商品Id skuIds
                for (String skuId : skuIds) {
                    // 当前商品在这个仓库中
                    // 转换方式 要么转换为long ，要么转换为String。
                    if (Long.parseLong(skuId)==orderDetail.getSkuId()){
                        // 获取到订单明细中的商品，然后放入子订单的订单明细集合
                        orderDetails.add(orderDetail);
                    }
                }
            }
            // 将子订单集合放入子订单
            subOrderInfo.setOrderDetailList(orderDetails);
            // 计算：调用方法
            subOrderInfo.sumTotalAmount();

            // 保存子订单数据
            saveOrderInfo(subOrderInfo);
            // 添加子订单到集合中
            subOrderInfoList.add(subOrderInfo);
        }
        // 修改订单状态
        updateOrderStatus(orderId,ProcessStatus.SPLIT);

        return subOrderInfoList;
    }

    // 返回发送的Json字符串
    public String initWareOrder(Long orderId) {
        // 所有的数据都在orderInfo 中
        OrderInfo orderInfo = getOrderInfo(orderId);
        // 但是，发送数据是orderInfo 中的部分数据。只要8个字段,我们将这个八个字段放入map中就可以了。
        Map map = initWareOrder(orderInfo);
        return JSON.toJSONString(map);
    }
    public Map initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("orderId",orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        map.put("wareId", orderInfo.getWareId());// 仓库Id ，减库存拆单时需要使用！
        /*
        details:[{skuId:101,skuNum:1,skuName:’小米手64G’},
                {skuId:201,skuNum:1,skuName:’索尼耳机’}]
                里面的数据一定来自于订单明细
         */
        // 声明一个集合来存储map
        List<Map> maps = new ArrayList<>();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put("skuId",orderDetail.getSkuId());
            hashMap.put("skuNum",orderDetail.getSkuNum());
            hashMap.put("skuName",orderDetail.getSkuName());
            // 完成数据组装
            maps.add(hashMap);
        }

        map.put("details",maps);
        // 返回map集合
        return map;
    }

    @Override
    public void execExpiredOrder(Long orderId, String flag) {
        // 调用方法 状态
        updateOrderStatus(orderId,ProcessStatus.CLOSED);
        if ("2".equals(flag)){
            // 发送消息队列，关闭支付宝的交易记录。
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
        }
    }

}

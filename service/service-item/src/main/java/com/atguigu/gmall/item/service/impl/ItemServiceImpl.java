package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private ListFeignClient listFeignClient;

    @Override
    public Map<String, Object> getBySkuId(Long skuId) {
        Map<String, Object> map = new HashMap<>();
        // 通过skuId 查询skuInfo
        CompletableFuture<SkuInfo> skuCompletableFuture = CompletableFuture.supplyAsync(() -> {
            //通过skuId 查询skuInfo
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            // 保存数据
            map.put("skuInfo", skuInfo);
             return skuInfo;
        }, threadPoolExecutor);

        // 销售属性-销售属性值回显并锁定
        CompletableFuture<Void> spuCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuInfo -> {
            List<SpuSaleAttr> spuSaleAttrList = productFeignClient.getSpuSaleAttrListCheckBySku(skuInfo.getId(), skuInfo.getSpuId());
            // 保存数据
            map.put("spuSaleAttrList", spuSaleAttrList);
        }, threadPoolExecutor);

        //获取商品最新价格
        CompletableFuture<Void> priceCompletableFuture = CompletableFuture.runAsync(() -> {
            //获取商品最新价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            // 获取价格
            map.put("price", skuPrice);
        }, threadPoolExecutor);


        // 获取分类数据
        CompletableFuture<Void> categoryViewCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuInfo -> {
            // 根据三级分类ID 获取分类数据
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            // 保存数据
            map.put("categoryView", categoryView);
        }, threadPoolExecutor);


        //根据spuId 查询map 集合属性
        CompletableFuture<Void>  valuesSkuJsonCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuInfo -> {
            Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
            // 保存 json字符串
            map.put("valuesSkuJson", JSON.toJSONString(skuValueIdsMap) );
        }, threadPoolExecutor);

        // 更新商品 incrHotScore 通过feign  远程调用商品热度的方法
        CompletableFuture<Void> incrHotScoreCompletableFuture  = CompletableFuture.runAsync(() -> {
            listFeignClient.incrHotScore(skuId);
        }, threadPoolExecutor);


        // 多任务组合
        CompletableFuture.allOf(
                skuCompletableFuture,
                spuCompletableFuture,
                priceCompletableFuture,
                categoryViewCompletableFuture,
                valuesSkuJsonCompletableFuture,
                incrHotScoreCompletableFuture

                ).join();
        return map;
    }
}

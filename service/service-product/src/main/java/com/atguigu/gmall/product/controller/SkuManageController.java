package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuImage;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "SKU管理接口")
@RestController
@RequestMapping("admin/product")
public class SkuManageController {

    @Autowired
    private ManageService manageService;

    //http://api.gmall.com/admin/product/spuImageList/{spuId}
    // 根据spuId获取spuImage 列表集合数据
    @GetMapping("spuImageList/{spuId}")
    public Result spuImageList(@PathVariable Long spuId){
        List<SpuImage> spuImageList = manageService.getSpuImageList(spuId);
        // 返回数据集合
        return Result.ok(spuImageList);

    }
    //http://api.gmall.com/admin/product/spuSaleAttrList/{spuId}
    //根据spuId获取销售属性

    @GetMapping("spuSaleAttrList/{spuId}")
    public Result spuSaleAttrList(@PathVariable Long spuId){
       List<SpuSaleAttr> spuSaleAttrList = manageService.getSpuSaleAttrList(spuId);

        return Result.ok(spuSaleAttrList);

    }
    // http://api.gmall.com/admin/product/saveSkuInfo
    @PostMapping("saveSkuInfo")
    public Result  saveSkuInfo(@RequestBody SkuInfo skuInfo){

        //调用方法
        manageService.saveSkuInfo(skuInfo);

        return Result.ok();
    }

    //  http://api.gmall.com/admin/product/list/ {page}/{limit}
    @GetMapping("list/{page}/{limit}")
    public Result getSkuList(@PathVariable Long page,
                             @PathVariable Long limit){
        // 封装分页条件
        Page<SkuInfo> skuInfoPage = new Page<SkuInfo>(page, limit);
        // 调用服务层方法
        IPage<SkuInfo> skuInfoIPageList = manageService.getSkuList(skuInfoPage);

        return Result.ok(skuInfoIPageList);

    }
    //http://api.gmall.com/admin/product/onSale/{skuId}
    @GetMapping("onSale/{skuId}")
        public Result onSale(@PathVariable Long skuId){
        // 调用服务层
        manageService.onSale(skuId);
        return  Result.ok();
        }
    @GetMapping("cancelSale/{skuId}")
    public Result cancelSale(@PathVariable Long skuId){
        // 调用服务层
        manageService.cancelSale(skuId);
        return  Result.ok();
    }
}

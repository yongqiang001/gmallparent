package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Api(tags = "商品属性接口")
@RestController
@RequestMapping("admin/product")
public class BaseManageController {

    @Autowired
    private ManageService manageService;

    // 获取一级分类数据
    @GetMapping("getCategory1")
    public Result getCategory1(){
        List<BaseCategory1> category1List = manageService.getCategory1();

        return Result.ok(category1List);
    }

    // 获取二级分类数据
    @GetMapping("getCategory2/{category1Id}")
    public Result getCategory2(@PathVariable("category1Id") Long category1Id ){
        List<BaseCategory2> category2List = manageService.getCategory2(category1Id);
        // 封装的返回结果集

        return Result.ok(category2List);

    }

    // 获取三级分类数据
    @GetMapping("getCategory3/{category2Id}")
    public Result getCategory3(@PathVariable Long category2Id){
        List<BaseCategory3> category3List = manageService.getCategory3(category2Id);

        return Result.ok(category3List);
    }

    // 根据分类ID 获取平台属性集合
    @GetMapping("attrInfoList/{category1Id}/{category2Id}/{category3Id}")
    public Result attrInfoList(@PathVariable Long category1Id,
                               @PathVariable Long category2Id,
                               @PathVariable Long category3Id){
        List<BaseAttrInfo> attrInfoList = manageService.getAttrInfoList(category1Id, category2Id, category3Id);
        return Result.ok(attrInfoList);
    }


    // 平台属性添加
    // 根据api 接口文档可以看出, 前台传递过来的json字符串转化为BaseAttrInfo
    @PostMapping("saveAttrInfo")
    public Result saveAttrInfo(@RequestBody BaseAttrInfo baseAttrInfo){

        //调用服务层数据
        manageService.saveAttrInfo(baseAttrInfo);

        return Result.ok();
    }


    // 根据平台属性id 获取平台属性值集合
//    http://api.gmall.com/admin/product/getAttrValueList/{attrId}
    @GetMapping("getAttrValueList/{attrId}")
    public Result getAttrValueList(@PathVariable Long attrId){
        //修改时, 必须要先确定是否有这个属性, 如果有这个属性, 我们需要从这个属性中来获取平台属性值集合
        // 上述描述从业务角度会更加严谨
        // attrId = baseAttrInfo.id
        BaseAttrInfo baseAttrInfo= manageService.getBaseAttrInfo(attrId);
        if(null != baseAttrInfo){
            // 从平台属性中自动获取到平台属性值集合
            List<BaseAttrValue> baseAttrValueList = baseAttrInfo.getAttrValueList();
            // 返回数据
            return Result.ok(baseAttrValueList);
        }
        return Result.fail();



//         这个方案:功能可以实现,但是不够严谨
//         根据当前的平台属性ID attrId = baseAttrInfo.id
//         select * from base_attr_value where attr_id = attrId;
//
//        List<BaseAttrValue> baseAttrValueList =  manageService.getAttrValueList(attrId);
//
//        return Result.ok(baseAttrValueList);

    }
}

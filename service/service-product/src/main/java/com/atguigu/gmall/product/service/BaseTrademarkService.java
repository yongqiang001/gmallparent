package com.atguigu.gmall.product.service;

import com.atguigu.gmall.model.product.BaseTrademark;

import com.baomidou.mybatisplus.core.metadata.IPage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface BaseTrademarkService extends IService<BaseTrademark> {
    /**
     * Banner 分页列表
     */


    // 需要做一个根据页面传递过来的参数进行分页的方法！
    IPage<BaseTrademark> getBaseTrademarkList(Page<BaseTrademark> param);

    // 获取品牌列表
//    List<BaseTrademark> getTrademarkList ();



}

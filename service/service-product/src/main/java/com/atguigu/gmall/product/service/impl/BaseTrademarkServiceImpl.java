package com.atguigu.gmall.product.service.impl;


import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.mapper.BaseTrademarkMapper;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BaseTrademarkServiceImpl  extends ServiceImpl<BaseTrademarkMapper, BaseTrademark>  implements BaseTrademarkService {


    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;


    @Override
    public IPage<BaseTrademark> getBaseTrademarkList(Page<BaseTrademark> param) {

        QueryWrapper<BaseTrademark> baseTrademarkQueryWrapper = new QueryWrapper<>();
        //设置一个查询后的规则
        baseTrademarkQueryWrapper.orderByDesc("id");
        return  baseTrademarkMapper.selectPage(param,baseTrademarkQueryWrapper);

    }



//    @Override
//    public List<BaseTrademark> getTrademarkList() {
//        return  baseTrademarkMapper.selectList(null);
//
//    }
}


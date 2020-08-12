package com.atguigu.gmall.ware.mapper;


import com.atguigu.gmall.ware.bean.WareSku;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @param
 * @return
 */
@Repository
public interface WareSkuMapper extends BaseMapper<WareSku> {

    Integer selectStockBySkuid(String skuid);

    int incrStockLocked(WareSku wareSku);

    int selectStockBySkuidForUpdate(WareSku wareSku);

    int  deliveryStock(WareSku wareSku);

    List<WareSku> selectWareSkuAll();
}

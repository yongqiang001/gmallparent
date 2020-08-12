package com.atguigu.gmall.product.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;


import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ManageServiceImpl implements ManageService {
    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;

    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;

    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;

    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;

    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;
    @Autowired
    private SpuInfoMapper spuInfoMapper;
    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;

    @Autowired
    private SpuImageMapper spuImageMapper;

    @Autowired
    private SpuSaleAttrMapper  spuSaleAttrMapper;

    @Autowired
    private SpuSaleAttrValueMapper  spuSaleAttrValueMapper;


    @Autowired
    private SkuInfoMapper skuInfoMapper;

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Autowired
    private SkuImageMapper skuImageMapper;

    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;

    @Autowired
    private BaseCategoryViewMapper baseCategoryViewMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;

    @Autowired
    private RabbitService rabbitService;




    @Override
    public List<BaseCategory1> getCategory1() {

        return baseCategory1Mapper.selectList(null);
    }

    @Override
    public List<BaseCategory2> getCategory2(Long category1Id) {
        // select * from baseCategory2 where Category1Id = ?
        QueryWrapper queryWrapper = new QueryWrapper<BaseCategory2>();
        queryWrapper.eq("category1_id", category1Id);
        List<BaseCategory2> baseCategory2List = baseCategory2Mapper.selectList(queryWrapper);
        return baseCategory2List;
    }

    @Override
    public List<BaseCategory3> getCategory3(Long category2Id) {
        // select * from baseCategory3 where Category2Id = ?
//
//        QueryWrapper queryWrapper = new QueryWrapper<BaseCategory3>( );
//        queryWrapper.eq("category2_id", category2Id);
//        List<BaseCategory3> baseCategory3List = baseCategory3Mapper.selectList(queryWrapper);

        return baseCategory3Mapper.selectList(new QueryWrapper<BaseCategory3>().eq("category2_id", category2Id));
    }

    @Override
    public List<BaseAttrInfo> getAttrInfoList(Long category1Id, Long category2Id, Long category3Id) {
        // 调用mapper：
        // 查询平台属性列表,需要编写一个复杂的sql, 多表管理查询{ 目的是为了后续有个功能, 能和现在这个功能结合在一起 }
        // 这个功能, 就是通过分类ID 查询平台属性, 同时也可以查询平台属性值;
        // 如果不跟后面的功能进行整合, 那么只需要单表查询就可以了
        // 借助mybatis 的配置文件进行多表关联查询
        return baseAttrInfoMapper.selectBaseAttrInfoList(category1Id, category2Id, category3Id);
    }

    // 保存平台属性
    @Override
    @Transactional
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        //baseAttrInfo  平台属性
        // 这个实现类, 既有保存,又有修改
        // 判断
        if(baseAttrInfo.getId() !=null){

            baseAttrInfoMapper.updateById(baseAttrInfo);
        }else {

            baseAttrInfoMapper.insert(baseAttrInfo);
        }



        //baseAttrValue  平台属性值
        // 修改平台属性值的时候,系统无法确认用户想改的数据是谁! 像这样的方式我们可以通过
        // 先删除.再新增
        // 根据平台属性ID 进行对应的属性值删除,然后再新增
        // delete from base_attr_value where attr_id =  baseAttrInfo.getId();
        baseAttrValueMapper.delete(new QueryWrapper<BaseAttrValue>().eq("attr_id", baseAttrInfo.getId()));


        // 获取平台属性中的属性值集合
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        //判断当前集合不能为空
        if (!CollectionUtils.isEmpty(attrValueList)){
            //循环遍历  (iter)
            for (BaseAttrValue baseAttrValue : attrValueList) {
                // 细节处理: 需要将attrId赋值, attrId = baseAttrInfo.id
                baseAttrValue.setAttrId(baseAttrInfo.getId());
                baseAttrValueMapper.insert(baseAttrValue);
            }
        }

    }

    @Override
    public List<BaseAttrValue> getAttrValueList(Long attrId) {
        // select * from base_attr_value where attr_id = attrId;
        return  baseAttrValueMapper.selectList(new QueryWrapper<BaseAttrValue>().eq("attr_id", attrId));



    }

    @Override
    public BaseAttrInfo getBaseAttrInfo(Long attrId) {
        // select * from base_attr_info where id = attr_id
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrId);
        if (null != baseAttrInfo){
            // 获取平台属性值集合, 放入attrValueList 属性中;
            baseAttrInfo.setAttrValueList(getAttrValueList(attrId));
        }


        return baseAttrInfo;
    }



    @Override
    public IPage<SpuInfo> getSpuInfoPage(Page<SpuInfo> param, SpuInfo spuInfo) {
        QueryWrapper<SpuInfo> spuInfoQueryWrapper = new QueryWrapper<>();
        spuInfoQueryWrapper.eq("category3_id",spuInfo.getCategory3Id());
        spuInfoQueryWrapper.orderByDesc("id");
        return spuInfoMapper.selectPage(param, spuInfoQueryWrapper);
    }

    @Override
    public List<BaseSaleAttr> getBaseSaleAttrlist() {
        // 获取所有的销售属性数据
        List<BaseSaleAttr> baseSaleAttrList = baseSaleAttrMapper.selectList(null);
        return baseSaleAttrList;
    }

    @Override
    @Transactional
    public void saveSpuInfo(SpuInfo spuInfo) {
        // 保存数据
        // spuInfo
        spuInfoMapper.insert(spuInfo);

        //spuImage  获取前台传来的图片列表
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if(!CollectionUtils.isEmpty(spuImageList)){
            //循环遍历
            for (SpuImage spuImage : spuImageList) {
                //注意一个细节  spuId 页面没有传递,
                // 问题, 第一次添加的时候, 前台页面传递过来的spuInfo.id 为null
                // 虽然传递过来的 时候给的是一个null,但是你插入了spuInfo 数据以后, 那么这个id就不为空
                // 因为spuInfo实体类中id属性type = IdType.AUTO, 说明数据一旦插入以后就能够获取到插入的id,


                spuImage.setSpuId(spuInfo.getId());
                spuImageMapper.insert(spuImage);

            }
        }
        // spuSaleAttr
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if (!CollectionUtils.isEmpty(spuSaleAttrList)){
            //循环遍历
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                 spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);

                //获取属性值集合
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
                if (!CollectionUtils.isEmpty(spuSaleAttrValueList)){
                    //循环遍历
                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {

                        spuSaleAttrValue.setSpuId(spuInfo.getId());

                        // 由于页面没有传递数据, 但是, 我们可以通过业务得知
                        // spu_sale_attr_value.sale_attr_name = spu_sale_attr. sale_attr_name
                        spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());

                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);
                    }
                }
            }
        }

    }

    @Override
    public List<SpuImage> getSpuImageList(Long spuId) {
        return spuImageMapper.selectList(new QueryWrapper<SpuImage>().eq("spu_id", spuId));


    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(Long spuId) {
        // 独立写  因为需要多表关联查询, 此时不能单独用mybatis-plus, 使用xml配置文件


        List<SpuSaleAttr> spuSaleAttrList = spuSaleAttrMapper.selectSpuSaleAttrList(spuId);
        return spuSaleAttrList;
    }

    @Override
    @Transactional
    public void saveSkuInfo(SkuInfo skuInfo) {
        // sku_info
        skuInfoMapper.insert(skuInfo);
        // skuImage
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if (!CollectionUtils.isEmpty(skuImageList)){
            //循环遍历
            for (SkuImage skuImage : skuImageList) {
                // 细节：需要将skuId 赋值，因为页面没有传递
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }

        }
        // skuAttrValue sku 与平台属性值的关系
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if (!CollectionUtils.isEmpty(skuAttrValueList)){
            // 循环遍历
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                // 细节：需要将skuId 赋值，因为页面没有传递
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }
        // skuSaleAttrValue sku 与 销售属性值的关系
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        if (!CollectionUtils.isEmpty(skuSaleAttrValueList)){
            // 循环遍历
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
                // 页面传递过来的值，我们只用saleAttrValueId，但是我们还需要spuId,skuId.
                skuSaleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId()); // 从哪里获取?
                skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }
        // 通过mq发送数据
        //商品上架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuInfo.getId());
    }

    @Override
    public IPage<SkuInfo> getSkuList(Page<SkuInfo> skuInfoPage) {
        // 可以按照某个排序规则进行分页查询 order by  id desc
        QueryWrapper<SkuInfo> skuInfoQueryWrapper = new QueryWrapper<>();
        skuInfoQueryWrapper.orderByDesc("id");
        // 调用Mapper的分页查询方法
        return skuInfoMapper.selectPage(skuInfoPage, skuInfoQueryWrapper);
    }

    @Override
    @Transactional
    public void onSale(Long skuId) {
        // is_sale
        // update sku_info set is_sale = 1 where id = skuId;
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(1);
        skuInfoMapper.updateById(skuInfo);  // 动态更新

        // 通过 mq 发送信息实现商品上架
        // 发送什么消息才能实现商品上架: 在service-list 中upperGoods(Long skuId)
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuId);



    }

    @Override
    public void cancelSale(Long skuId) {
        // cancelSale
        // update sku_info set cancelSale = 1 where id = skuId;
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(0);
        skuInfoMapper.updateById(skuInfo);

        //商品下架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_LOWER, skuId);

    }

    @Override
    @GmallCache(prefix = "sku")
    public SkuInfo getSkuInfo(Long skuId) {
        return getSkuInfoDB(skuId);
    }

    // 表示使用redis set+lua 脚本实现的分布式锁！
    private SkuInfo getSkuInfoRedisSet(Long skuId) {
        SkuInfo skuInfo = null;
        try {
            // 添加缓存部分！ 先查询缓存，如果缓存没有再查询数据，并将数据放入缓存！
            //        if(true){
            //            // 缓存
            //        }else {
            //            // db --- 放入缓存！
            //        }
            // 定义一个缓存的key
            // key = sku:skuId:info
            String skuKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKUKEY_SUFFIX;
            // 获取缓存的数据
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            // 判断缓存中的数据是否为空
            if (skuInfo==null){
                // 说明缓存没有数据，没有数据, db --- 放入缓存!
                // 在从数据库获取数据的时候，我们需要添加一个锁，为了保证在高并发情况下防止缓存击穿。
                //  redis - set ，lua 脚本  | 使用Redissson
                //  lockKey = sku:skuId:lock
                String lockKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKULOCK_SUFFIX;
                // 设置UUID
                String uuid = UUID.randomUUID().toString();
                // 执行上锁命令
                Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);
                // 判断上锁是否成功
                if (flag){ // 表示上锁成功了！
                    skuInfo = getSkuInfoDB(skuId); // 可能会出现什么问题？
                    // 缓存穿透！
                    if (skuInfo==null){
                        // 设置一个空对象进去,这个数据应该给一个短暂的过期时间
                        SkuInfo skuInfo1 = new SkuInfo();
                        redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                        // 返回不存在的对象
                        return skuInfo1;
                    }
                    // 将从数据库查询到数据放入缓存 商品详情页面数据，应该有个过期时间
                    redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                    // 获取完成之后：删除对应的锁！ 使用lua 脚本
                    // 这个脚本只在客户端传入的值和键的口令串相匹配时
                    String script="if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    // 执行这个lua 脚本
                    DefaultRedisScript defaultRedisScript = new DefaultRedisScript();
                    // 将lua脚本放入对象
                    defaultRedisScript.setScriptText(script);
                    // 设置一个返回值
                    defaultRedisScript.setResultType(Long.class);
                    redisTemplate.execute(defaultRedisScript, Arrays.asList(lockKey),uuid);
                    // 返回真正的数据
                    return skuInfo;
                }else {
                    // 睡眠 表示这个线程没有获取到锁！
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // 继续获取数据
                    return getSkuInfo(skuId);
                }
            }else {
                // 表示缓存中有数据！ 从缓存中获取
                return skuInfo;
            }
        } catch (Exception e) {
            // 如果有日志系统的话，那么这个地方应该记录日志。
            e.printStackTrace();
        }
        // 如果缓存宕机了？redisTemplate 以及获取数据的时候都会出现异常！
        return getSkuInfoDB(skuId);
    }

    private SkuInfo getSkuInfoDB(Long skuId) {
        // skuId = skuInfo.id
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);

        if(skuInfo!=null){
            // 获取商品的图片信息
            // select * from sku_image where sku_id=skuId
            List<SkuImage> skuImageList = skuImageMapper.selectList(new QueryWrapper<SkuImage>().eq("sku_id", skuId));
            skuInfo.setSkuImageList(skuImageList);
        }
        // 返回skuInfo 对象数据！
        return skuInfo;
    }

    // 获取分类信息
    @Override
    @GmallCache(prefix = "categoryView")
    public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {
        return  baseCategoryViewMapper.selectById(category3Id);

    }


    // 获取价格信息
    @Override
    @GmallCache(prefix = "price")
    public BigDecimal getSkuPrice(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if (null != skuInfo){
            return skuInfo.getPrice();
        }
        return new BigDecimal("0");
    }

    //获取销售信息
    @Override
    @GmallCache(prefix = "spuSaleAttrListCheckBySku")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {


        return  spuSaleAttrMapper.selectSpuSaleAttrListCheckBySku(skuId, spuId);

    }

    @Override
    @GmallCache(prefix = "skuValueIdsMap")
    public Map getSkuValueIdsMap(Long spuId) {
        Map<Object, Object> map = new HashMap<>();
        List<Map> mapList = skuSaleAttrValueMapper.selectSaleAttrValuesBySpu(spuId);

        if (mapList !=null && mapList.size() > 0){
            // 遍历循环
            for (Map skuMap : mapList) {
                // key = 125 | 123 , value = 37
                map.put(skuMap.get("value_ids"), skuMap.get("sku_id"));


            }
        }
        return map;
    }

    @Override
    @GmallCache(prefix = "getBaseCategoryList")
    public List<JSONObject> getBaseCategoryList() {
        // 声明一个json 对象集合
        List<JSONObject> list = new ArrayList<>();
        // 获取所有分类数据: 一级二级三级
        List<BaseCategoryView> baseCategoryViewList = baseCategoryViewMapper.selectList(null);
        // 获取一级二级三级分类中的部分数据
        // 按照一级分类ID 进行分组  Collectors.groupingBy(BaseCategoryView::getCategory1Id)
        Map<Long, List<BaseCategoryView>> category1Map = baseCategoryViewList.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));
        // 构建json 数据
        int index = 1;
        // 一级分类Id的名称  categoryName 数据
        for (Map.Entry<Long, List<BaseCategoryView>> entry1 : category1Map.entrySet()) {

            Long category1Id = entry1.getKey();

            // 获取一级分类的名称 categoryName
            List<BaseCategoryView> category2List = entry1.getValue();
            String category1Name = category2List.get(0).getCategory1Name();

            // 声明一个JSONObject 将一级分类数据存储上
            JSONObject category1 = new JSONObject();
            category1.put("index", index);
            category1.put("categoryName", category1Name);
            category1.put("categoryId", category1Id);

            //category1.put("categoryChild", category2List);  // 因为二级分类还没有  稍等一会儿再添加
            // 让 index 进行 ++ 操作, 更新一级分类的个数
            index ++ ;
            // 声明一个集合才存储一级分类下所有的二级分类数据
            List<JSONObject> category2Child = new ArrayList<>();
            // 获取二级分类数据! key = 二级分类ID value = List<BaseCategoryView>
            Map<Long, List<BaseCategoryView>> category2Map = category2List.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
            // 循环遍历category2Map 获取二级分类Id, 二级分类名称
            for (Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()) {
                // 二级分类Id
                Long category2Id = entry2.getKey();
                // 获取二级分类的名称 categoryName
                List<BaseCategoryView> category3List = entry2.getValue();
                String category2Name = category3List.get(0).getCategory2Name();

                // 声明一个JSONObject 将一级分类数据存储上
                JSONObject category2 = new JSONObject();
                category2.put("categoryName", category2Name);
                category2.put("categoryId", category2Id);
                // 二级分类下还有三级分类集合, 三级分类还没有获取到
                //category2.put("categoryChild", category3List);
                // 将每次循环的二级分类数据, 放入了这个集合
                category2Child.add(category2);

                // 声明一个集合来存储一级分类下所有的二级分类数据
                List<JSONObject> category3Child = new ArrayList<>();
                // 获取三级分类数据  由于三级分类Id 没有重复的, 不需要分组了
                // Consumer void accept(T t);
                category3List.stream().forEach(category3View ->{
                    // 声明一个JSONObject 将三级分类数据存储上
                    JSONObject category3 = new JSONObject();
                    // 获取三级分类Id和名称
                    category3.put("categoryId", category3View.getCategory3Id());
                    category3.put("categoryName", category3View.getCategory3Name() );
                    // 将获取到的三级分类数Id 放入集合。
                    category3Child.add(category3);
                });
                // 将三级分类数据集合放入二级分类数据
                category2.put("categoryChild",category3Child);

            }
            // 将二级分类数据集合放入一级分类数据
            category1.put("categoryChild",category2Child);
            // 将所有的一级分类数据放入到Json 集合中
            list.add(category1);
        }

        // 返回Json 对象集合
        return list;
    }

    @Override
    public BaseTrademark getTrademarkByTmId(Long tmId) {
        return baseTrademarkMapper.selectById(tmId);
    }

    @Override
    public List<BaseAttrInfo> getAttrList(Long skuId) {
        return baseAttrInfoMapper.selectBaseAttrInfoListBySkuId(skuId);
    }


}

package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author mqx
 * @date 2020-8-3 14:50:03
 */
@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private ProductFeignClient productFeignClient;
    // 保存上架
    // 使用es有个现成的工具类。
    @Autowired
    private GoodsRepository goodsRepository; // 对应的curd 方法。

    @Autowired
    private RedisTemplate redisTemplate;

    // 引入操作es 的类，专门编写dsl 语句。
    @Autowired
    private RestHighLevelClient restHighLevelClient;


    @Override
    public void upperGoods(Long skuId) {
        /*
        1.	从skuInfo 中获取部分信息！
        2.	获取分类数据！
        3.	获取品牌数据！
        4.	获取平台属性-平台属性值！
         */
        Goods goods = new Goods();
        // 给goods 中每一项赋值
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        // 判断skuInfo 不为空
        if (null!=skuInfo){ // 推荐使用这种方式判断！
            goods.setId(skuInfo.getId());
            goods.setTitle(skuInfo.getSkuName());
            goods.setCreateTime(new Date());
            goods.setPrice(skuInfo.getPrice().doubleValue());
            goods.setDefaultImg(skuInfo.getSkuDefaultImg());

            // 获取品牌信息
            BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
            if (null!=trademark){
                goods.setTmId(trademark.getId());
                goods.setTmName(trademark.getTmName());
                goods.setTmLogoUrl(trademark.getLogoUrl());
            }
            // 获取分类数据
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            if (null!=categoryView){
                goods.setCategory1Id(categoryView.getCategory1Id());
                goods.setCategory1Name(categoryView.getCategory1Name());
                goods.setCategory2Id(categoryView.getCategory2Id());
                goods.setCategory2Name(categoryView.getCategory2Name());
                goods.setCategory3Id(categoryView.getCategory3Id());
                goods.setCategory3Name(categoryView.getCategory3Name());
            }
        }
        // 获取平台属性
        List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuId);

        // 判断当前集合不能为空
        if (!CollectionUtils.isEmpty(attrList)){
            // 有属性：有属性值
            // Function   R apply(T t);
            List<SearchAttr> searchAttrList = attrList.stream().map(baseAttrInfo -> {
                /*
                存储的 List<SearchAttr> 这个集合
                SearchAttr
                private Long attrId;
                private String attrValue;
                private String attrName;
             */
                SearchAttr searchAttr = new SearchAttr();
                // 平台属性Id
                searchAttr.setAttrId(baseAttrInfo.getId());
                // 平台属性名称
                searchAttr.setAttrName(baseAttrInfo.getAttrName());
                // 平台属性值名称
                // 获取平台属性值集合对象
                List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
                // searchAttr.setAttrValue(BaseAttrValue.valueName);
                // 为什么get(0) 一个sku 对应的平台属性，对应的平台属性值只有一个！
                // 1700-2799 | skuId = 35  价格  | 1700-2799
                String valueName = attrValueList.get(0).getValueName();
                searchAttr.setAttrValue(valueName);
                return searchAttr;
            }).collect(Collectors.toList());
            // 获取平台属性集合
            goods.setAttrs(searchAttrList);
        }
        goodsRepository.save(goods);
    }

    // 下架
    @Override
    public void lowerGoods(Long skuId) {
        goodsRepository.deleteById(skuId);
    }

    @Override
    public void incrHotScore(Long skuId) {
        // 更新热度排名 ，redis
        // 使用redis 的哪种数据类型！
        // 定义一个缓存的key
        String hotKey = "hotScore";
        // 用户访问完，数据加完之后的结果
        Double count = redisTemplate.opsForZSet().incrementScore(hotKey, "skuId:" + skuId, 1);
        // 判断什么时候更新es
        if (count%10==0){
            // 更新es 将原有数据中的 "hotScore" : 0 改为最新的
            Optional<Goods> optional = goodsRepository.findById(skuId);
            Goods goods = optional.get();
            goods.setHotScore(Math.round(count));
            // 保存到es
            goodsRepository.save(goods);
        }
    }

    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {
        /*
         实现思路：
         1. 先获取到动态生产的dsl 语句
         2. 利用这个dsl 语句进行查询
         3. 将查询到的结果集转换为我们需要的对象SearchResponseVo
         */
        SearchRequest searchRequest = this.buildQueryDsl(searchParam);
        // 执行dsl 语句
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        // 将查询到结果进行转换 searchResponse --- > SearchResponseVo
        // searchResponseVo.SetTotal(searchResponse.total);
        SearchResponseVo searchResponseVo = parseSearchResult(searchResponse);

        // 给searchResponseVo 对象赋值 给默认值
        searchResponseVo.setPageSize(searchParam.getPageSize());
        searchResponseVo.setPageNo(searchParam.getPageNo());
        // 赋值总页数 10,3,4  9,3,3 必须先知道一共有多少条数据
        // Long totalPages = searchResponseVo.getTotal()%searchParam.getPageSize()==0?(searchResponseVo.getTotal()/searchParam.getPageSize()):(searchResponseVo.getTotal()/searchParam.getPageSize()+1);
        // 在开发中还有其他公式
        Long totalPages = (searchResponseVo.getTotal()+searchParam.getPageSize()-1)/searchParam.getPageSize();
        searchResponseVo.setTotalPages(totalPages);
        return searchResponseVo;
    }

    // 查询结果转换
    private SearchResponseVo parseSearchResult(SearchResponse searchResponse) {
        // 声明一个对象
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        /*
          private List<SearchResponseTmVo> trademarkList;
          private List<SearchResponseAttrVo> attrsList = new ArrayList<>();
          private List<Goods> goodsList = new ArrayList<>();
          private Long total;//总记录数
         */

        // 获取品牌数据，获取销售属性： 从聚合中数据获取！因为这个地方没有重复数据！
        Map<String, Aggregation> aggregationMap = searchResponse.getAggregations().asMap();
        // Aggregation 这个接口不能获取到getBuckets() 数据，所以我们需要将Aggregation 转为ParsedLongTerms
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        // 获取品牌数据集合
        List<SearchResponseTmVo> tmVoList = tmIdAgg.getBuckets().stream().map(bucket -> {
            // 声明一个品牌对象
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            // "key" : 2
            String tmId = bucket.getKeyAsString();
            searchResponseTmVo.setTmId(Long.parseLong(tmId));
            // 获取品牌的名称
            Map<String, Aggregation> tmIdMap = bucket.getAggregations().getAsMap();
            // Aggregation 这个接口不能获取到getBuckets() 数据所以我们需要将Aggregation 转为ParsedStringTerms
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmIdMap.get("tmNameAgg");
            // key 对应的数据只有一条所以在此获取集合的时候get(0)
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);

            // 获取品牌的logoUrl
            ParsedStringTerms tmLogoUrlAgg = (ParsedStringTerms) tmIdMap.get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();

            searchResponseTmVo.setTmLogoUrl(tmLogoUrl);
            // 返回品牌数据
            return searchResponseTmVo;
        }).collect(Collectors.toList());
        // 将品牌数据放入返回对象中！
        searchResponseVo.setTrademarkList(tmVoList);
        // 获取商品信息集合
        SearchHits hits = searchResponse.getHits(); // 获取单独hit节点数据
        SearchHit[] subHits = hits.getHits();
        // 声明一个存储商品的集合
        List<Goods> goodsList = new ArrayList<>();
        if (null!= subHits && subHits.length>0){
            // 循环遍历
            for (SearchHit subHit : subHits) {
                // subHit.getSourceAsString(); // 相当于获取到source ，而这个source 对应的实体类Goods
                Goods goods = JSONObject.parseObject(subHit.getSourceAsString(), Goods.class);
                // 细节： 由于source 中的title 不是高亮，如果是通过全文检索的模式，我们必须获取高亮字段！ highlight
                if (subHit.getHighlightFields().get("title")!=null){
                    // 说明高亮中有数据，对应要获取到数据
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    // 将title 的高亮进行替换
                    goods.setTitle(title.toString());
                }
                // 把查询的sku数据添加到集合。
                goodsList.add(goods);
            }
        }
        // 放入商品集合
        searchResponseVo.setGoodsList(goodsList);
        // 获取对应的商品平台属性数据集合
        // attrAgg 属于nested 数据类型 -- Aggregation数据转化ParsedNested
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        // 获取 attrIdAgg 数据
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        // 准备获取attrIdAgg 下面的数据
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            // 使用流失编程将集合转化为map ，为了好获取数据
            List<SearchResponseAttrVo> attrsList = buckets.stream().map(bucket -> {
                // 声明一个平台属性对象
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                // "key" : 1
                Number attrId = bucket.getKeyAsNumber();
                searchResponseAttrVo.setAttrId(attrId.longValue());

                // 获取平台属性名称的聚合
                // Aggregation 转化为 ParsedStringTerms 获取buckets 集合数据
                ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attrNameAgg");
                String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
                // 赋值平台属性名称
                searchResponseAttrVo.setAttrName(attrName);

                // 获取平台属性值集合
                // Aggregation 转化为 ParsedStringTerms 获取buckets 集合数据
                ParsedStringTerms attrValueAgg = bucket.getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> valueBucketsList = attrValueAgg.getBuckets();
                // 获取聚合中的每个数据， 先将集合转化为map，将 key 获取，有了key 就能够获取到value数据了。
                // Terms.Bucket::getKeyAsString  map.get("key")
                List<String> valueList = valueBucketsList.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                // 赋值平台属性值集合
                searchResponseAttrVo.setAttrValueList(valueList);
                // 返回对象
                return searchResponseAttrVo;
            }).collect(Collectors.toList());
            searchResponseVo.setAttrsList(attrsList);
        }
        // 赋值总条数 hits.totalHits = total
        searchResponseVo.setTotal(hits.totalHits);

        return searchResponseVo;
    }

    // 根据用户输入的检索条件得到查询dsl。
    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        // 根据手动编写的dsl 语句入手
        // 构建dsl 查询器 {}
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 构建QueryBuilder 对象
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // bool --->filter
        // 判断一级分类Id
        if (!StringUtils.isEmpty(searchParam.getCategory1Id())){
            // filter ---> term
            TermQueryBuilder category1Id = QueryBuilders.termQuery("category1Id", searchParam.getCategory1Id());
            boolQueryBuilder.filter(category1Id);
        }
        // 判断二级分类Id
        if (!StringUtils.isEmpty(searchParam.getCategory2Id())){
            // filter ---> term
            TermQueryBuilder category2Id = QueryBuilders.termQuery("category2Id", searchParam.getCategory2Id());
            boolQueryBuilder.filter(category2Id);
        }
        // 判断三级分类Id
        if (!StringUtils.isEmpty(searchParam.getCategory3Id())){
            // filter ---> term
            TermQueryBuilder category3Id = QueryBuilders.termQuery("category3Id", searchParam.getCategory3Id());
            boolQueryBuilder.filter(category3Id);
        }


        // bool --->must
        // 判断 http://list.gmall.com/list.html?keyword=荣耀
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            // must --> match 参数还是需要QueryBuilder
            // MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("title",searchParam.getKeyword());
            // 例如我们检索 荣耀手机： 分词 荣耀，手机  Operator.AND，也就是说title 中分词【荣耀，手机】 必须同时存在，才会查询到。
            // Operator.OR 有其中一个即可！
            MatchQueryBuilder title = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.AND);
            boolQueryBuilder.must(title);
        }

        // 关于品牌的：查询 trademark=2:华为
        String trademark = searchParam.getTrademark();
        if (!StringUtils.isEmpty(trademark)){
            String[] split = trademark.split(":");
            // 判断数据格式
            if (null!=split && split.length==2){
                // filter--->term ---> "tmId": "4"
                boolQueryBuilder.filter(QueryBuilders.termQuery("tmId",split[0]));
            }
        }
        // 平台属性查询
        // 获取平台属性
        // 页面传递数据 http://list.gmall.com/list.html?keyword=荣耀&props=23:4G:运行内存&props=24:128G:机身内存
        String[] props = searchParam.getProps();
        if (null!=props && props.length>0){
            // 循环遍历
            for (String prop : props) {
                // prop = 23:4G:运行内存 , prop=24:128G:机身内存
                // 先拆分数据 属性Id ，属性值名称，属性名称
                String[] split = prop.split(":");
                // 判断split 数据格式是否正确
                if (null!=split && split.length==3){
                    // 构建bool
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    // 构建bool 设置子查询条件
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    // 构建查询条件
                    // must-->term
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",split[0])); // 属性Id
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",split[1])); // 属性值名称

                    // 设置nested 查询
                    // bool --> must -->nested
                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));
                    // filter --> bool --> must -->nested
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        // 分页
        // 设置起始页！ 公式：(pageNo-1)*pageSize
        int from = (searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());
        // 高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.preTags("<span style=color:red>");
        highlightBuilder.postTags("</span>");
        // 将设置好的高亮对象放入
        searchSourceBuilder.highlighter(highlightBuilder);
        // 排序
        // 默认按照热度排序，那么我们电商页面有选择排序！ 按照价格，销量等排序
        // 1=hotScore 2=price  如果在页面点击的是综合=1 价格=2
        // 页面传递数据：http://list.gmall.com/list.html?keyword=荣耀&props=23:4G:运行内存&order=2:desc
        // &order=1:asc 表示按照热度升序排序 ，&order=1:desc 按照热度降序排列 &order=2:desc 表示按照价格降序排序.
        // order= 1:asc  1:表示点击的哪个字段   asc：表示排序规则
        String order = searchParam.getOrder();
        // 判断
        if (!StringUtils.isEmpty(order)){
            // 按照：进行拆分
            String[] split = order.split(":");
            // 判断格式
            if (null!=split && split.length==2){
                // 声明一个排序的字段
                String field = null;
                switch (split[0]){
                    case "1":
                        field="hotScore";
                        break;
                    case "2":
                        field="price";
                        break;
                }
                // 设置es 排序规则
                // field 表示按照哪个字段排序 ，三元表达式：判断页面传递的asc ，那么我们就给SortOrder.ASC ，否则就给SortOrder.DESC
                searchSourceBuilder.sort(field,"asc".equals(split[1])? SortOrder.ASC:SortOrder.DESC);
            }else {
                // 如果格式不正确：http://list.gmall.com/list.html?keyword=荣耀&props=23:4G:运行内存&order=2 给默认排序规则
                searchSourceBuilder.sort("hotScore",SortOrder.DESC);
            }
        }

        // 聚合
        // 一组是品牌 ，一组是平台属性值
        // terms 中是我们自定义的变量名，field=一定是 goods 中的字段！
        TermsAggregationBuilder tmIdTermsAggregationBuilder =
                AggregationBuilders.terms("tmIdAgg").field("tmId")
                        .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                        .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));
        searchSourceBuilder.aggregation(tmIdTermsAggregationBuilder);

        // 平台属性值聚合是属于nested 数据类型！
        // nested 两个参数，第一个参数是给一个变量名称，第二个参数哪个字段是nested 数据类型，
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

        // 调用query 方法 参数QueryBuilder
        // query --- bool
        searchSourceBuilder.query(boolQueryBuilder);

        // 结果集的过滤 查询的结果集中我只要 "id","defaultImg","title","price" 四个字段
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);
        // 在那个index,type 中执行dsl语句
        // GET /index/type/_search   GET /goods/info/_search
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        // 将整个查询器放入source 中！
        searchRequest.source(searchSourceBuilder);
        System.out.println("dsl:"+searchSourceBuilder.toString());
        // 返回执行对象
        return searchRequest;
    }
}

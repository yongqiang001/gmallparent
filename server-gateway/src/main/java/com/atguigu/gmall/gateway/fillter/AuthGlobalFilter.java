package com.atguigu.gmall.gateway.fillter;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author mqx
 * 自定义一个拦截器
 * @date 2020-8-7 10:07:44
 */
@Component
public class AuthGlobalFilter implements GlobalFilter {

    // 引入redisTemplate
    @Autowired
    private RedisTemplate redisTemplate;
    @Value("${authUrls.url}")
    private String authUrls; // authUrls=trade.html,myOrder.html,list.html
    // 创建匹配对象
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    // 拦截过滤方法
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 先获取用户输入的URL地址：
        ServerHttpRequest request = exchange.getRequest();
        // 获取到了！
        String path = request.getURI().getPath();
        // 这个path 中不能访问  api/product/inner/getSkuInfo/{skuId} 接口数据
        if(antPathMatcher.match("/**/inner/**",path)){
            // 设置一个响应
            ServerHttpResponse response = exchange.getResponse();
            // 调用方法
            return out(response, ResultCodeEnum.PERMISSION);
        }
        // 访问哪些控制器时，需要用户登录！
        // 获取用户Id ，如果有用户Id 则说明登录，没有就没有登录！
        String userId = getUserId(request);
        String userTempId = getUserTempId(request);
        // 判断 token,中的uuId 是否被盗用！
        if ("-1".equals(userId)){
            // 设置一个响应
            ServerHttpResponse response = exchange.getResponse();
            // 调用方法
            return out(response, ResultCodeEnum.PERMISSION);
        }
        // 判断用户如果访问 /api/**/auth/** 这样的控制器内部数据接口，则是不允许{在未登录的情况下不允许访问}
        // http://order.gmall.com/api/order/auth/1 内部控制器数据接口，那么你必须登录！
        if (antPathMatcher.match("/api/**/auth/**",path)){
            // 如果是未登录情况
            if (StringUtils.isEmpty(userId)){
                // 设置一个响应
                ServerHttpResponse response = exchange.getResponse();
                // 调用方法
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }

        // 最后一步，需要验证用户访问web-all 时，需要走的控制器！ 必须在配置文件中获取！
        // 判断 trade.html{订单},myOrder.html{查看我的订单},list.html{为了测试用，检索，实际项目是不需要}
        // http://list.gmall.com/list.html?category3Id=61  http://list.gmall.com/trade.html
        // authUrls=trade.html,myOrder.html,list.html
        for (String authUrl : authUrls.split(",")) {
            // 判断访问的url 中是否包含上述的数据
            // 用户访问的url 中包含上述数据，并且你的用户Id 是空，未登录
            if (path.indexOf(authUrl)!=-1 && StringUtils.isEmpty(userId)){
                // 设置一个响应
                ServerHttpResponse response = exchange.getResponse();
                // 提示你需要登录！
                // 表示由于请求对应的资源存在着另一个url，需要重定向！
                response.setStatusCode(HttpStatus.SEE_OTHER);
                // 数据设置 ,
                response.getHeaders().set(HttpHeaders.LOCATION,"http://www.gmall.com/login.html?originUrl="+request.getURI());
                // 设置完成之后，重定向！
                return response.setComplete();
            }
        }
        // 如果说用户登录了呢，那么我们就需要将 userId 传递后台服务
        if (!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)){
            if (!StringUtils.isEmpty(userId)){
                // 将用户Id 放入header 中。
                request.mutate().header("userId",userId).build();
            }
            if (!StringUtils.isEmpty(userTempId)){
                // 将临时用户Id 放入header 中。
                request.mutate().header("userTempId",userTempId).build();
            }
            // 将request 对象，变成exchange对象。
            // exchange.mutate().request(request).build();
            return chain.filter(exchange.mutate().request(request).build());
        }
        // 这个就不需要变了。
        return chain.filter(exchange);
    }

    // 获取临时用Id，在cookie，或者是 header 中！ 在登录时，会走请求拦截器： config.headers['userTempId'] = auth.getUserTempId();
    private String getUserTempId(ServerHttpRequest request) {
        // 获取header中的数据
        String userTempId = "";
        List<String> list = request.getHeaders().get("userTempId");
        // 判断集合不为空！
        if (!CollectionUtils.isEmpty(list)){
            userTempId = list.get(0);
        }else {
            // 表示在header 中没有获取到数据，从cookie中获取
            HttpCookie httpCookie = request.getCookies().getFirst("userTempId");
            if (httpCookie!=null){
                userTempId = httpCookie.getValue();
            }
        }
        return userTempId;
    }

    // 获取用户Id
    private String getUserId(ServerHttpRequest request) {
        // 用户Id 存储在缓存中， String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+uuid;
        // uuid 存在cookie 中，还有一个地方也存储了。header!
        // new Cookie("token",uuid); config.headers['token'] = auth.getToken();
        // 声明一个uuid
        // 因为：cookie 针对pc 端！ 如果说移动端：cookie？ 那么就可以设置header。
        String uuid = "";
        List<String> list = request.getHeaders().get("token");
        // 如果从header 中获取到了cookie 则获取里面的数据
        if (!CollectionUtils.isEmpty(list)){
            // 因为token 中只对应一个数据！
            uuid = list.get(0);
        }else {
            // 从header 中没有获取到token 对应的uuid,那么应该从cookie 中获取！
            // new Cookie("token",uuid); cookie 获取数据：get()通过指定的key来获取。 getFirst() 表示获取cookie 中第一个数据
            HttpCookie cookie = request.getCookies().getFirst("token");
            if (cookie!=null){
                // 获取到key=token 中所对应的value 值
                uuid = cookie.getValue();
            }
        }
        // 如果获取到了uuid ,那么我们就可以判断用户在缓存中是否有数据！
        if (!StringUtils.isEmpty(uuid)){
            // 定义用户Key
            String userKey = "user:login:"+uuid;
            // 获取数据 "{\"ip\":\"192.168.200.1\",\"userId\":\"2\"}"
            String userStrJson = (String) redisTemplate.opsForValue().get(userKey);
            // 将获取到的字符串 转化为 JSONObject
            JSONObject userJson = JSONObject.parseObject(userStrJson);
            // 通过key 来获取数据 userId,ip
            String ip = userJson.getString("ip");
            // 判断你用户登录时获取到的电脑ip 与存储时的ip 是否一致！ 如果一致，则才能获取到userId,如果不一致返回一个“-1”
            // 获取到登录时ip
            String ipAddress = IpUtil.getGatwayIpAddress(request);
            // 防止cookie 被盗用所以做了一步判断
            if (ip.equals(ipAddress)){
                // 将获取到的userId 返回去
                return userJson.getString("userId");
            }else {
                return  "-1";
            }
        }
        return null;
    }
    // 输入信息到页面
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {
        // 页面的信息是来自于ResultCodeEnum
        Result<Object> result = Result.build(null, resultCodeEnum);
        // 需要将result 这个对象写入页面，但是result 是一个对象，我们需要对他进行转化{字符集}
        // "utf-8" 有异常就捞一下
        byte[] bytes = JSONObject.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        // 将字节数组变为一个数据流
        DataBuffer wrap = response.bufferFactory().wrap(bytes);
        // 设置一下页面的头部信息
        response.getHeaders().add("Content-Type","application/json;charset=UTF-8");
        // 使用response 写到页面
        return response.writeWith(Mono.just(wrap));

    }
}

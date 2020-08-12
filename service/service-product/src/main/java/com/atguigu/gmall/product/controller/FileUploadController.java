package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import io.swagger.annotations.Api;
import org.apache.commons.io.FilenameUtils;
import org.csource.fastdfs.ClientGlobal;
import org.csource.fastdfs.StorageClient1;
import org.csource.fastdfs.TrackerClient;
import org.csource.fastdfs.TrackerServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FilenameFilter;
@Api(tags = "上传接口")
@RestController
@RequestMapping("admin/product")
public class FileUploadController {
    //
    //文件上传必须指定文件的服务器地址
    @Value("${fileServer.url}")  //叫软编码, 将可能会发生变化的数据写在配置文件中, 软编码
    private String fileUrl;

    // http://api.gmall.com/admin/product/fileUpload
    // 利用springMVC上传知识
    // file与后台管理系统页面对应
    @RequestMapping("fileUpload")
    public Result<String> fileUpload (MultipartFile file) throws Exception{
        // 读取配置文件 tracker.conf
        String configFile = this.getClass().getResource("/tracker.conf").getFile();
        String path = "";
    if (null != configFile){
        //初始化
        ClientGlobal.init(configFile);
        // 创建tracker
        TrackerClient trackerClient = new TrackerClient();
        // 用 trackerClient 获取连接
        TrackerServer trackerServer = trackerClient.getConnection();


        //创建storageClient
        StorageClient1 storageClient1 = new StorageClient1(trackerServer, null);

        // 调用上传方法
        // 第一个参数表示上传文件的字节数组, 第二参数表示文件后缀名  zly.jpg
        String extName = FilenameUtils.getExtension(file.getOriginalFilename());
        // 返回一个文件上传的路径(文件所在的路径)
        path = storageClient1.upload_appender_file1(file.getBytes(), extName, null);

        System.out.println("上传文件之后返回的完整路径: \t"+ fileUrl + path);



    }
    // 返回文件路径
        return Result.ok(fileUrl+path);
    }
}

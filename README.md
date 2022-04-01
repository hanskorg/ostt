# OSTT
> Object Storage Transfer Tool
> 
## Introduction
对象存储文件迁移工具, 支持多个**云对象存储**文件互传,旨在简化简化不同对象存储之间文件互相迁移。

目前支持的云端对象存储
- [x] 阿里云 OSS
- [x] 网宿 WOS
- [x] 七牛云
- [x] 腾讯云 COS
- [x] 百度云 BOS
- [ ] AWS S3

## Usage
本工具暂不提供UI，文件迁移状态使用Mysql数据库存储

### 1、安装
#### 选择版本
[osstransfer.tar.gz](https://github.com/hanskorg/ostt/releases)
##### 1、with jre版本
java version 1.8.0_131
##### 2、without jre版本
```
wget https://github.com/hanskorg/strorage-transfer/releases/download/v0.1.0/transfer-0.1.0-without-jre.tar.gz
tar zxvf transfer-0.1.0-without-jre.tar.gz ./transfer
cd transfer
ln -s {path_to_jre} jre
./transfer start
```

### 2、修改配置 config/application.yml
> 下面是配置文件参考，更多配置项参照 [Config.java](src/main/java/org/hansk/tools/transfer/Config.java "Config.java")


配置文件简述, **不同对象存储**transfer.<provider>配置略有不同，请注意。
```
spring:
  datasource:
    url: jdbc:mysql://<host>:<port>/transfer
    username: <user>
    password: <password>
...

#迁移相关相关配置, AK/SK 需要配置好迁移双方
transfer:
  oss:
    key: key
    secret: secret
    endpoint: http://endpoint
    timeout: 1000
    access_domain: cdn.example.com
  qiniu:
    accessKey: 
    secretKey: 
  cos:
    secretId: 
    secretKey: 
    appId: 
    region: 
  bos:
    accessKey: 
    secretKey: 
    endpoint: gz.bcebos.com
  wos:
    accessKeyId: 
    accessKeySecret: 
    endpoint:
    region: 
  buckets:
  -
    #源存储,必填
    originStorage: 
    #源存储Bucket ,必填
    originBucket: 
    #源Region COS有效 非必填, 默认值 transfer.cos.region
    originRegion:
    #源EndPoint OSS有效 非必填 默认值 transfer.oss.end_point
    originEndPoint: 
    #需要迁移的对象前缀 非必填 默认值 /
    prefix:

    #目标存储 多值,分割，默认值为transfer.target
    targetStorage: cos
    #目标bucket 非必填 默认与源相同
    targetBucket: 
    #目标bucket 非必填 transfer.cos.region
    targetRegion: 
     #目标bucket 非必填 transfer.oss.end_point
    targetEndPoint:

  #最小网络线程
  core_download: 2
  #最大网络线程
  max_download: 50
  #迁移2018-10-19 00:00:00之前的对象
  transferBefore: 2018-10-19 00:00:00
```

参照配置: 

- [网宿迁移至 BOS(百度云) + COS(腾讯云)](./oss-bos-cos.md)

### 运行

> ./transfer start # 启动

> ./transfer stop # 停止
 


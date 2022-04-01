### Demo WOS -> COS+BOS
> 本配置案例 把文件从网宿WOS 迁移到 百度BOS 和腾讯COS

参照配置如下

```yaml application.yaml

logging:
  config: config/log4j.properties
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/transfer
    username: root
    password: root
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    filters: stat
    maxActive: 20
    initialSize: 1
    maxWait: 60000
    minIdle: 1
    timeBetweenEvictionRunsMillis: 60000
    minEvictableIdleTimeMillis: 300000
    validationQuery: select 1
    testWhileIdle: true
    testOnBorrow: false
    testOnReturn: false
    poolPreparedStatements: true
    maxOpenPreparedStatements: 2
    useSSL: false
    data: classpath:transfer.sql
    initialization-mode: always
mybatis:
  mapper-locations: classpath:mapping/*.xml
  type-aliases-package: org.hansk.tools.transfer.domain
transfer: #迁移相关相关配置
  cos:
    secretId: <cos secret_id>
    secretKey: <cos secret_key>
    appId: <cos app_id>
    region: ap-beijing
  wss:
    accessKeyId: <wos key>
    accessKeySecret: <wos secret>
    endpoint: http://s3-cn-south-5.wcsapi.com
    region: cn-south-5
  bos:
    accessKey: <bos key>
    secretKey: <bos secret>
    endPoint: bd.bcebos.com # 默认Endpoint
  #需要迁移bucket列表,可配置多项
  buckets:
    -
      originStorage: wos
      originBucket: video-upload
      prefix:
        - news/ # 新闻图片
        - static/p0- 
      #目标存储
      targetStorage: bos
      targetBucket: res
      targetRegion: gz
      targetEndPoint:
    -
      originStorage: wos
      originBucket: video-upload
      originRegion: cn-south-5
      prefix:
        - video/ # 视频
      #目标存储
      targetStorage: cos
      #目标bucket，默认与源相同
      targetBucket: voide
      targetRegion: ap-beijing
  #最小传输线程
  minTransThread: 2
  #最大传输线程
  maxTransThread: 100
  #检查对象存在的最大线程数
  maxCheckThread: 10
  #迁移transferBefore时间点之前的对象
  transferBefore: 2022-03-19 00:00:00


```
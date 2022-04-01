package org.hansk.tools.transfer.action;

import com.aliyun.oss.ClientConfiguration;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.bouncycastle.util.Strings;
import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.service.TransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by guohao on 2018/5/17.
 */
public class FetchOssObjectRunner implements ApplicationRunner {

    @Autowired
    public TransferService transferService;

    public void setConfig(Config config) {
        this.config = config;
    }

    @Autowired
    private Config config;
    private Logger logger = LoggerFactory.getLogger(FetchOssObjectRunner.class);
    private Map<String, OSSClient> ossClients;
    private Runnable fetchRunner;
    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {

        ossClients = new HashMap<>();

        fetchRunner = new Runnable(){

            @Override
            public void run() {
                if(config.getOssKey() == ""){
                    return;
                }
                boolean isTruncated = false;
                ClientConfiguration clientConfiguration = new ClientConfiguration();
                clientConfiguration.setConnectionTimeout(config.getOssTimeout());
                clientConfiguration.setRequestTimeout(config.getOssTimeout());

                for (Config.Bucket bucket : config.getBuckets()){
                    if(!Strings.toUpperCase(bucket.getOriginStorage()).equals("OSS") ){
                        continue;
                    }

                    OSSClient ossClient       = null;
                    if(!ossClients.containsKey(bucket.getOriginBucket())){
                        COSCredentials cred       = new BasicCOSCredentials(config.getCosSecretID(), config.getCosSecretKey());
                        ClientConfig clientConfig = new ClientConfig(new Region(config.getCosRegion(bucket.getOriginBucket())));
                        ossClients.put(
                                bucket.getOriginBucket(),
                                new OSSClient(config.getOssEndPoint(), config.getOssKey(), config.getOssSecret(), clientConfiguration)
                        );
                    }
                    ossClient = ossClients.get(bucket.getOriginBucket());

                    String bucketName = bucket.getOriginBucket();
                    if(bucket.getPrefix().isEmpty()){
                        bucket.getPrefix().add(null);
                    }
                    for(String prefix : bucket.getPrefix()){
                        String optionFlag = "next_marker-" + "oss" + "-"+ bucketName + "-" + prefix;
                        String nextMarker = transferService.getOption(optionFlag);
                        if(nextMarker != null && nextMarker.equals("-1")){
                            logger.info("==== "+ bucket.getOriginStorage() +":" + prefix + " 已经遍历完毕 =====");
                            continue;
                        }
                        logger.info("==== "+ bucket.getOriginStorage() +":" + prefix + " =====");
                        do {
                            try {
                                ObjectListing objectListing = ossClient.listObjects(new ListObjectsRequest(bucketName).withPrefix(prefix).withMarker(nextMarker).withMaxKeys(500));

                                List<OSSObjectSummary> sums = objectListing.getObjectSummaries();
                                for (OSSObjectSummary s : sums) {
                                    if(s.getLastModified().before(config.getTransferBefore())){
                                        transferService.preTransferNotTransfer("OSS",bucket.getOriginBucket(), bucket.getTargetStorage(), bucket.getTargetBucket(), s.getKey(), s.getSize(), null);
                                        logger.info("[" + s.getBucketName() + " ,"+ s.getKey() + "]");
                                    }
                                }

                                nextMarker  = objectListing.getNextMarker();
                                transferService.setOption(optionFlag, nextMarker == null ? "-1" : nextMarker);
                                isTruncated = objectListing.isTruncated();
                            }catch (OSSException ex){
                                logger.error("oss fail : " + ex.getMessage());
                            }catch (ClientException ex){
                                logger.error("client fail : " + ex.getMessage());
                            } finally {
                                //ossClient.shutdown();
                                //ossClient = new OSSClient(config.getEndPoint(), config.getOssKey(), config.getOssSecret(), clientConfiguration);
                            }

                        }while (isTruncated);
                    }

                }
                for (Map.Entry<String, OSSClient> entry: ossClients.entrySet()) {
                    entry.getValue().shutdown();
                }
            }
        };
        Thread thread = new Thread(fetchRunner);
        thread.setName("OSS-Fetch");
        thread.start();
    }

}

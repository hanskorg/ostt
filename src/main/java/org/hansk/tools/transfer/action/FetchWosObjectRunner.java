package org.hansk.tools.transfer.action;


import com.wos.services.WosClient;
import com.wos.services.WosConfiguration;
import com.wos.services.model.ListObjectsRequest;
import com.wos.services.model.ObjectListing;
import com.wos.services.model.S3Object;
import org.bouncycastle.util.Strings;
import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.service.TransferService;
import org.hansk.tools.transfer.storage.WOSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FetchWosObjectRunner implements ApplicationRunner {
    @Autowired
    public TransferService transferService;

    public void setConfig(Config config) {
        this.config = config;
    }

    @Autowired
    private Config config;
    private Logger logger = LoggerFactory.getLogger(FetchCosObjectRunner.class);
    private WOSClient wosClient;
    private Runnable fetchRunner;
    private Map<String, com.wos.services.WosClient> wosClients;
    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {

        wosClients = new HashMap<>();
        fetchRunner = new Runnable(){

            @Override
            public void run() {
                if(config.getWosAccessKey() == ""){
                    return;
                }
                for (Config.Bucket bucket : config.getBuckets()){
                    if(!Strings.toUpperCase(bucket.getOriginStorage()).equals("WOS")){
                        continue;
                    }
                    com.wos.services.WosClient wosClient       = null;
                    if(!wosClients.containsKey(bucket.getOriginBucket())){
                      WosConfiguration wosConfig =  new WosConfiguration();
                        wosConfig.setEndPoint(config.getWosEndPoint(bucket.getOriginBucket()));
                        wosConfig.setRegionName(!bucket.getOriginRegion().equals("") ? bucket.getOriginRegion() : config.getWosRegion());
                        wosClients.put(bucket.getOriginBucket(), new com.wos.services.WosClient(
                        config.getWosAccessKey(),
                        config.getWosSecretKey(), wosConfig
                        ));
                    }
                    wosClient = wosClients.get(bucket.getOriginBucket());

                    if(bucket.getPrefix().isEmpty()){
                        bucket.getPrefix().add(null);
                    }
                    for(String prefix : bucket.getPrefix()){
                        String optionFlag = "next_marker-" + "wos" + "-"+bucket.getOriginBucket() + "-" + prefix;
                        String nextMarker = transferService.getOption(optionFlag);
                        if(nextMarker != null && nextMarker.equals("-1")){
                            logger.info("[Bucket: "+ bucket + ", Prefix: "+ prefix + "]已经遍历完毕 =====");
                            continue;
                        }
                        boolean isTruncated = false;

                        do {
                            ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucket.getOriginBucket(), prefix, nextMarker, "", 1000);
                            ObjectListing objectListing = wosClient.listObjects(listObjectsRequest);
                            nextMarker = objectListing.getNextMarker();
                            //TODO fucking wos， isTruncated always == true
                            if (objectListing.isTruncated() && nextMarker.equals("")) {
                                transferService.setOption(optionFlag, "-1");
                                logger.info("[Bucket: " + bucket + ", Prefix: " + prefix + "]已经遍历完毕 =====");
                                isTruncated = true;
                            }else{
                                transferService.setOption(optionFlag, nextMarker);
                            }

                            for (S3Object object : objectListing.getObjectSummaries()) {
                                if (object.getMetadata().getLastModified().before(config.getTransferBefore())) {
                                    transferService.preTransferNotTransfer("WOS", bucket.getOriginBucket(),bucket.getTargetStorage(),  bucket.getTargetBucket(), object.getObjectKey(), object.getMetadata().getContentLength(), null);
                                    logger.info("PreTransfer: [" + bucket.getOriginBucket() + " ," + object.getObjectKey() + "]");
                                }
                            }

                        }while (isTruncated);
                    }
                }
                for (Map.Entry<String, WosClient> entry: wosClients.entrySet()) {
                    try {
                        entry.getValue().close();
                    } catch (IOException e) {
                        logger.error("client shutdown exception:" + e.getMessage());
                    }
                }
            }
        };
        Thread thread = new Thread(fetchRunner);
        thread.setName("WOS-Fetch");
        thread.start();
    }
}

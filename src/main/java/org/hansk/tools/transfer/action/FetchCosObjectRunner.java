package org.hansk.tools.transfer.action;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
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
import java.util.Map;

/**
 * COS 遍历
 * @author guohao
 */
public class FetchCosObjectRunner implements ApplicationRunner {
    @Autowired
    public TransferService transferService;

    public void setConfig(Config config) {
        this.config = config;
    }

    @Autowired
    private Config config;
    private Logger logger = LoggerFactory.getLogger(FetchCosObjectRunner.class);
    private COSClient cosClient;
    private Runnable fetchRunner;
    private Map<String, COSClient> cosClients;
    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {

        cosClients = new HashMap<>();
        fetchRunner = new Runnable(){

            @Override
            public void run() {
                if(config.getCosSecretID() == ""){
                    return;
                }
                for (Config.Bucket bucket : config.getBuckets()){
                    if(!Strings.toUpperCase(bucket.getOriginStorage()).equals("COS")){
                        continue;
                    }
                    COSClient cosClient       = null;
                    if(!cosClients.containsKey(bucket.getOriginBucket())){
                        COSCredentials cred       = new BasicCOSCredentials(config.getCosSecretID(), config.getCosSecretKey());
                        ClientConfig clientConfig = new ClientConfig(new Region(config.getCosRegion(bucket.getOriginBucket())));
                        cosClients.put(bucket.getOriginBucket(), new COSClient(cred, clientConfig));
                    }
                    cosClient = cosClients.get(bucket.getOriginBucket());

                    if(bucket.getPrefix().isEmpty()){
                        bucket.getPrefix().add(null);
                    }
                    for(String prefix : bucket.getPrefix()){
                        String optionFlag = "next_marker-" + "cos" + "-"+bucket.getOriginBucket() + "-" + prefix;
                        String nextMarker = transferService.getOption(optionFlag);
                        if(nextMarker != null && nextMarker.equals("-1")){
                            logger.info("[Bucket: "+ bucket + ", Prefix: "+ prefix + "]已经遍历完毕 =====");
                            continue;
                        }
                        boolean isTruncated = false;

                        do {
                            ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucket.getOriginBucket(), prefix, nextMarker, "", 1000);
                            ObjectListing objectListing = cosClient.listObjects(listObjectsRequest);
                            nextMarker = objectListing.getNextMarker();
                            if (objectListing.isTruncated()) {
                                transferService.setOption(optionFlag, "-1");
                                logger.info("[Bucket: " + bucket + ", Prefix: " + prefix + "]已经遍历完毕 =====");
                                isTruncated = true;
                            }else{
                                transferService.setOption(optionFlag, nextMarker);
                            }

                            for (COSObjectSummary object : objectListing.getObjectSummaries()) {
                                if (object.getLastModified().before(config.getTransferBefore())) {
                                    transferService.preTransferNotTransfer("cos", bucket.getOriginBucket(),bucket.getTargetStorage(),  bucket.getTargetBucket(), object.getKey(), object.getSize(), null);
                                    logger.info("PreTransfer: [" + bucket.getOriginBucket() + " ," + object.getKey() + "]");
                                }
                            }

                        }while (isTruncated);
                    }
                }
                for (Map.Entry<String, COSClient> entry: cosClients.entrySet()) {
                    entry.getValue().shutdown();
                }
            }
        };
        Thread thread = new Thread(fetchRunner);
        thread.setName("COS-Fetch");
        thread.start();
    }
}

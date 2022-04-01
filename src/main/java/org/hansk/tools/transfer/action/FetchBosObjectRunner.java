package org.hansk.tools.transfer.action;

import com.baidubce.services.bos.BosClient;
import com.baidubce.services.bos.BosClientConfiguration;
import com.baidubce.services.bos.model.BosObjectSummary;
import com.baidubce.services.bos.model.ListObjectsRequest;
import com.baidubce.services.bos.model.ListObjectsResponse;
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

public class FetchBosObjectRunner implements ApplicationRunner {
    @Autowired
    public TransferService transferService;

    public void setConfig(Config config) {
        this.config = config;
    }

    @Autowired
    private Config config;
    private Logger logger = LoggerFactory.getLogger(FetchCosObjectRunner.class);
    private com.baidubce.services.bos.BosClient BosClient;
    private Runnable fetchRunner;
    private Map<String, BosClient> bosClients;
    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {

        bosClients = new HashMap<>();
        fetchRunner = new Runnable(){

            @Override
            public void run() {
                if(config.getBosAccessKeyId() == ""){
                    return;
                }
                for (Config.Bucket bucket : config.getBuckets()){
                    if(!Strings.toUpperCase(bucket.getOriginStorage()).equals("Bos")){
                        continue;
                    }
                    BosClient bosClient       = null;
                    if(!bosClients.containsKey(bucket.getOriginBucket())){
                        BosClientConfiguration bsoConfig =  new BosClientConfiguration();
                        bsoConfig.setEndpoint(config.getBosEndpoint(bucket.getOriginBucket()));
                    }
                    bosClient = bosClients.get(bucket.getOriginBucket());

                    if(bucket.getPrefix().isEmpty()){
                        bucket.getPrefix().add(null);
                    }
                    for(String prefix : bucket.getPrefix()){
                        String optionFlag = "next_marker-" + "Bos" + "-"+bucket.getOriginBucket() + "-" + prefix;
                        String nextMarker = transferService.getOption(optionFlag);
                        if(nextMarker != null && nextMarker.equals("-1")){
                            logger.info("[Bucket: "+ bucket + ", Prefix: "+ prefix + "]已经遍历完毕 =====");
                            continue;
                        }
                        boolean isTruncated = false;

                        do {
                            ListObjectsRequest request = new ListObjectsRequest(bucket.getOriginBucket(), prefix);
                            request.setMarker(nextMarker);
                            ListObjectsResponse listObjectsResponse = bosClient.listObjects(request);

                            nextMarker = listObjectsResponse.getNextMarker();
                            if (listObjectsResponse.isTruncated()) {
                                transferService.setOption(optionFlag, "-1");
                                logger.info("[Bucket: " + bucket + ", Prefix: " + prefix + "]已经遍历完毕 =====");
                                isTruncated = true;
                            }else{
                                transferService.setOption(optionFlag, nextMarker);
                            }

                            for (BosObjectSummary object : listObjectsResponse.getContents()) {
                                if (object.getLastModified().before(config.getTransferBefore())) {
                                    transferService.preTransferNotTransfer("bos", bucket.getOriginBucket(),bucket.getTargetStorage(),  bucket.getTargetBucket(), object.getKey(), object.getSize(), null);
                                    logger.info("PreTransfer: [" + bucket.getOriginBucket() + " ," + object.getKey() + "]");
                                }
                            }

                        }while (isTruncated);
                    }
                }
                for (Map.Entry<String, BosClient> entry: bosClients.entrySet()) {
                    entry.getValue().shutdown();
                }
            }
        };
        Thread thread = new Thread(fetchRunner);
        thread.setName("Bos-Fetch");
        thread.start();
    }
}

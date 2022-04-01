package org.hansk.tools.transfer.action;


import com.qiniu.common.QiniuException;
import com.qiniu.common.Zone;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.storage.model.FileListing;
import com.qiniu.util.Auth;
import org.bouncycastle.util.Strings;
import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.service.TransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 *
 * @author hans <mailto:hans@hansk.org>
 */
public class FetchQiniuObjectRunner implements ApplicationRunner {
    @Autowired
    private Config config;
    private Logger logger = LoggerFactory.getLogger(FetchQiniuObjectRunner.class);
    private Runnable fetchRunner;
    @Autowired
    public TransferService transferService;
    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {
        fetchRunner = new Runnable(){

            @Override
            public void run() {
                if(config.getQiniuAccess() == ""){
                    return;
                }
                Auth auth = Auth.create(config.getQiniuAccess(), config.getQiniuSecret());
                Configuration cfg = new Configuration(Zone.zone1());
                BucketManager bucketManager = new BucketManager(auth,cfg);
                for (Config.Bucket bucket : config.getBuckets()){
                    if(!"QINIU".equals(Strings.toUpperCase(bucket.getOriginStorage()))){
                        continue;
                    }
                    if(bucket.getPrefix().isEmpty()){
                        bucket.getPrefix().add(null);
                    }
                    for(String prefix : bucket.getPrefix()){
                        String optionFlag = "next_marker-" + "qiniu" + "-"+bucket.getOriginBucket() + "-" + prefix;
                        String nextMarker = transferService.getOption(optionFlag);
                        if(nextMarker != null && "-1".equals(nextMarker)){
                            logger.info("[Bucket: "+ bucket + ", Prefix: "+ prefix + "]已经遍历完毕 =====");
                            continue;
                        }
                        boolean isTruncated = false;
                        do {
                            try {
                                FileListing fileList = bucketManager.listFiles(bucket.getOriginBucket(), prefix, nextMarker, 1000, "");

                                for (FileInfo fileInfo : fileList.items) {
                                    if (fileInfo.putTime /10000  < config.getTransferBefore().getTime()) {
                                        transferService.preTransferNotTransfer("qiniu", bucket.getOriginBucket(), bucket.getTargetStorage(),bucket.getTargetBucket(), fileInfo.key, fileInfo.fsize, bucket.getOriginCDNDomain());
                                        logger.info("PreTransfer: [" + bucket.getOriginStorage() + " ," + fileInfo.key + "]");
                                    }
                                }
                                nextMarker = fileList.marker;
                                if (fileList.isEOF()) {
                                    transferService.setOption(optionFlag, "-1");
                                    logger.info("[Bucket: " + bucket + ", Prefix: " + prefix + "]已经遍历完毕 =====");
                                    isTruncated = true;
                                }else{
                                    transferService.setOption(optionFlag, nextMarker);
                                }

                            } catch (QiniuException e) {
                                e.printStackTrace();
                                logger.error("七牛列举失败，1S后重试");
                            }
                        }while (!isTruncated);
                    }


                }
            }
        };
        Thread thread = new Thread(fetchRunner);
        thread.setName("QN-Fetch");
        thread.start();

    }
}

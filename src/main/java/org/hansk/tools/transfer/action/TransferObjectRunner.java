package org.hansk.tools.transfer.action;

import org.bouncycastle.util.Strings;
import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.domain.Transfer;
import org.hansk.tools.transfer.service.TransferService;
import org.hansk.tools.transfer.storage.IStorage;
import org.hansk.tools.transfer.storage.QiniuClient;
import org.hansk.tools.transfer.storage.TransferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by guohao on 2018/10/15.
 */
public class TransferObjectRunner implements ApplicationRunner {

    private Logger logger = LoggerFactory.getLogger(TransferObjectRunner.class);
    @Autowired
    private Config config;
    @Autowired
    public TransferService transferService;

    @Autowired
    private org.hansk.tools.transfer.storage.QiniuClient qiniuClient;
    @Autowired
    private org.hansk.tools.transfer.storage.COSClient cosClient;
    @Autowired(required = false)
    private org.hansk.tools.transfer.storage.OSSClient ossClient;
    @Autowired
    private org.hansk.tools.transfer.storage.WOSClient wosClient;
    @Autowired
    private org.hansk.tools.transfer.storage.BOSClient bosClient;
    @Autowired
    private ApplicationContext applicationContext;

    private ScheduledExecutorService scheduledThreadPoolExecutor;
    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {

        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        RejectedExecutionHandler rejectionHandler = new RejectedExecutionHandler(){

            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                synchronized (this) {
                    try {
                        scheduledThreadPoolExecutor.awaitTermination(500,TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        ThreadPoolExecutor executorPool = new ThreadPoolExecutor(config.getCoreDownloadThread(),
                config.getMaxDownloadThread(),
                10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100), threadFactory, rejectionHandler);
        scheduledThreadPoolExecutor = Executors.newSingleThreadScheduledExecutor();
        scheduledThreadPoolExecutor.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                List<Transfer> unTransferList =  transferService.getUnTransfers(config.getMaxDownloadThread() *2);
                for (Transfer transfer : unTransferList){
                    executorPool.execute( new Runnable(){
                        @Override
                        public void run() {
                            IStorage.StorageObject storageObject = null;
                            IStorage uploadClient = null;
                            IStorage downClient = null;
                            downClient = (IStorage) applicationContext.getBean(Strings.toUpperCase(transfer.getProvider()));
                            try {
                                storageObject = downClient.getObject(transfer);
                            } catch (Exception e) {
                                logger.error(transfer.getProvider() + " object get  error" + e.toString());
                                e.printStackTrace();
                                int times = transfer.getRetryTimes().incrementAndGet();
                                transfer.setStatus(404);
                                transferService.updateTransferStatus(transfer.getId(), transfer.getTargetProvider(), transfer.getStatus());
                                return;
                            }
                            List<String> targets = transfer.getTargetProvider() == null || transfer.getTargetProvider().isEmpty()
                                    ? config.getTarget()
                                    : Arrays.asList(transfer.getTargetProvider().split(","));
                            for (String target : targets){
                                uploadClient = (IStorage) applicationContext.getBean(Strings.toUpperCase(target));
                                boolean isOk = false;
                                try {

                                    isOk = uploadClient.putObject(storageObject.getContent()
                                            ,transfer.getTargetBucket()
                                            ,transfer.getObject()
                                            ,storageObject.getContentLength() != 0 ? storageObject.getContentLength() :transfer.getObjectSize()
                                            ,storageObject.getContentMD5()
                                            ,null
                                            );
                                } catch (Exception e) {
                                    transfer.setStatus(502);
                                    e.printStackTrace();
                                }
                                if( isOk ) {
                                    transfer.setStatus(1);
                                    logger.info("file upload success ["+ transfer.getProvider() + ":"+ transfer.getBucket() + " " + target + ":" + transfer.getTargetBucket()+" ;"+ transfer.getObject() + " ]");
                                }else{
                                    logger.error("file upload fail ["+ transfer.getProvider() + "; "+ transfer.getBucket() + " ;"+ transfer.getObject() + " ]");
                                }
                                if(transfer.getObjectSize() > 0 ){
//                                    transferService.putObject(transfer.getProvider()
//                                            ,transfer.getBucket()
//                                            ,transfer.getObject()
//                                            ,transfer.getObjectSize()
//                                            ,storageObject.getLastModified()
//                                            ,storageObject.getExpirationTime()
//                                            ,storageObject.getMetadata()
//                                            ,storageObject.getContentMD5()
//                                            ,0
//                                    );
                                    transferService.putObject(transfer.getTargetProvider()
                                            ,transfer.getTargetBucket()
                                            ,transfer.getObject()
                                            ,storageObject.getContentLength()
                                            ,storageObject.getLastModified()
                                            ,storageObject.getExpirationTime()
                                            ,storageObject.getMetadata()
                                            ,storageObject.getContentMD5()
                                            ,0
                                    );
                                    transferService.updateTransferStatus(transfer.getId(), transfer.getTargetProvider(), transfer.getStatus());
                                }else{
                                    transferService.updateTransferStatus(transfer.getId(), transfer.getTargetProvider(), storageObject.getContentLength(), transfer.getStatus());
                                }
                            }
                            if (storageObject != null && storageObject.getContent() != null){
                                try {
                                    storageObject.getContent().close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                }
            }
        },5000, 500, TimeUnit.MILLISECONDS);



    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public ScheduledExecutorService getScheduledThreadPoolExecutor() {
        return scheduledThreadPoolExecutor;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}

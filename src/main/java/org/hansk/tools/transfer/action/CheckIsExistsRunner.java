package org.hansk.tools.transfer.action;

import org.bouncycastle.util.Strings;
import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.domain.StorageObject;
import org.hansk.tools.transfer.domain.Transfer;
import org.hansk.tools.transfer.service.TransferService;
import org.hansk.tools.transfer.storage.IStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Stack;
import java.util.concurrent.*;

/**
 * 验证object是否存在
 * @author hans<mailto:hans@hansk.org>
 * @date 2018/10/25
 */
public class CheckIsExistsRunner implements ApplicationRunner {
    @Autowired
    private Config config;
    @Autowired
    private TransferService transferService;
    @Autowired
    private ApplicationContext applicationContext;

    private ScheduledExecutorService scheduledThreadPoolExecutor;

    private Logger logger = LoggerFactory.getLogger(CheckIsExistsRunner.class);


    private ConcurrentLinkedQueue<StorageObject> objectList;

    public CheckIsExistsRunner(){
        objectList = new ConcurrentLinkedQueue<>();
    }
    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        Executors.newFixedThreadPool(10);
        scheduledThreadPoolExecutor = Executors.newScheduledThreadPool(config.getMaxCheckThread());
        scheduledThreadPoolExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                IStorage storageClient = null;
                StorageObject object = objectList.poll();
                if(object == null){
                    return;
                }
                storageClient = (IStorage) applicationContext.getBean(Strings.toUpperCase(object.getProvider()));
                try {
                    boolean isExist = storageClient.isObjectExist(object.getBucketName(), object.getObjectKey());
                    logger.info(String.format("file check success, bucket:%s key: %s exist: %b", object.getBucketName(), object.getObjectKey(), isExist));
                    if(isExist){
                        transferService.updateObjectStatus(object.getId(), ObjectStatus.EXITS.getValue());
                    }else{
                        transferService.updateObjectStatus(object.getId(), ObjectStatus.NOT_EXITS.getValue());
                    }
                }catch (Exception ex){
                    logger.info(String.format("file check fail, bucket:%s key: %s", object.getBucketName(), object.getObjectKey()));
                }
            }
        },2,10,TimeUnit.MILLISECONDS);
        this.startFetchObjectList();
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public void startFetchObjectList(){
        Runnable fetchRunable = new Runnable() {
            @Override
            public void run() {
                int index = 0;
                while(!scheduledThreadPoolExecutor.isShutdown()){
                    int maxFetchNum = config.getMaxCheckThread() * 2;
                    List<StorageObject> objectList = transferService.getObjectList(0, index, maxFetchNum);
                    int sleepMillis = 0;
                    if(objectList.size() == 0){
                        sleepMillis = 30000;

                    }
                    if(CheckIsExistsRunner.this.objectList.size() > maxFetchNum){
                        sleepMillis = 100;
                    }
                    if(sleepMillis > 0){
                        try {
                            Thread.sleep(sleepMillis);
                            logger.info(String.format("no objects here wait %.2f", (float)sleepMillis / 1000));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }else{
                        CheckIsExistsRunner.this.objectList.addAll(objectList);
                        index += maxFetchNum;
                    }

                }
            }
        };
        new Thread(fetchRunable,"Obj-Exists-Checker").start();
    }
    public ScheduledExecutorService getScheduledThreadPoolExecutor() {
        return scheduledThreadPoolExecutor;
    }

    enum ObjectStatus{
        UNKNOW(0),
        EXITS(1),
        EXECPTION(2),
        NOT_EXITS(404);
        private int value;
        ObjectStatus(int i) {
            value = i;
        }

        public int getValue() {
            return value;
        }
    }

}

package org.hansk.tools.transfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.dao.ObjectsMapper;
import org.hansk.tools.transfer.dao.OptionsMapper;
import org.hansk.tools.transfer.dao.TransferMapper;
import org.hansk.tools.transfer.domain.StorageObject;
import org.hansk.tools.transfer.domain.Transfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by guohao on 2018/5/17.
 */
@Service
public class TransferService {
    @Autowired
    private TransferMapper transferMapper;
    @Autowired
    private OptionsMapper optionsMapper;
    @Autowired
    private ObjectsMapper objectMapper;
    @Autowired
    private Config config;

    public void preTransfer(String provider, String bucket, String targetProvider,  String targetBucket, String object, long objectSize, String cdnDomain){
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:s");
        if(targetBucket == null || targetBucket.equals("")){
            targetBucket = bucket;
        }
        transferMapper.createTransfer(provider, bucket, targetProvider, targetBucket, object, objectSize, cdnDomain, format.format(new Date()));
    }
    public void preTransferNotTransfer(String provider, String bucket, String targetProvider, String targetBucket, String objectName, long objectSize, String cdnDomain){

        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:s");
        List<String> targets = null;
        if(targetProvider == null || targetProvider.isEmpty()){
            targets = config.getTarget();
        }else{
            targets = Arrays.asList(targetProvider.split(","));
        }
        for (String target: targets){
            if(transferMapper.transferCount(bucket, objectName, target) == 0) {
                transferMapper.createTransfer(provider, bucket, target, targetBucket, objectName, objectSize,cdnDomain, format.format(new Date()));
            }
        }

    }

    public List<Transfer> getUnTransfers(String provider, String bucket, int limit){
        return transferMapper.getUnTransfer(provider, bucket, 0 , limit);
    }

    public List<Transfer>  getUnTransfers(int limit){
        return transferMapper.getUnTransfer(null, null,0 , limit);
    }

    public boolean isTransfered(String bucket, String objectName){
        return transferMapper.transferCount(bucket, objectName,null) == 0;
    }

    public List<Transfer> getTransferedList(String provider, String bucket, int status, int limit){
        return transferMapper.getObjectListByStatus(provider, bucket, status , limit);
    }

    public boolean updateTransferStatus(int id, String targetProvider, int status){
        transferMapper.updateTransferStatus( id, targetProvider, status);
        return true;
    }
    public boolean updateTransferStatus(int id, String targetProvider, long objectSize, int status){
        transferMapper.updateTransferStatusWithSize( id, targetProvider, objectSize, status);
        return true;
    }

    public List<StorageObject> getObjectList(String provider, String bucket, int status, int limit){
        return this.objectMapper.findByStatus(provider, bucket, status, 0, limit);
    }
    public List<StorageObject> getObjectList( int status, int start, int limit){
        return this.objectMapper.findByStatus(null, null,status, start, limit);
    }
    public List<StorageObject> getObjectList(  int start, int limit){
        return this.objectMapper.findObject(null, null, null, start, limit);
    }

    public boolean putObject(String provider, String bucket, String key, long size, Date createTime , Date expireTime, Map<String,Object> metaData, String hash, int status){
        StorageObject object = new StorageObject();
        object.setAddTime(createTime);
        object.setBucketName(bucket);
        object.setProvider(provider);
        object.setObjectKey(key);
        object.setSize(size);
        object.setExpires(expireTime);
        object.setStartTime(createTime);
        object.setMetaData(metaData);
        object.setFileMD5(hash);
        object.setLastCheckStatus(status);
        return this.objectMapper.insert(object);
    }

    public boolean putObjectBatch(List<StorageObject> storageObjectList){
        return this.objectMapper.insertAll(storageObjectList);
    }
    public void updateObjectStatus(int id, int status){
        this.objectMapper.updateStatus(id, status);
    }

    public String getOption(String key){
        return optionsMapper.getValue(key);
    }

    public Boolean setOption(String key, String value){
        return optionsMapper.setValue(key, value);
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public void setTransferMapper(TransferMapper transferMapper) {
        this.transferMapper = transferMapper;
    }

    public void setOptionsMapper(OptionsMapper optionsMapper) {
        this.optionsMapper = optionsMapper;
    }

    public void setObjectMapper(ObjectsMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}

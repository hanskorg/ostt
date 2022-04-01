package org.hansk.tools.transfer.domain;

import com.alibaba.fastjson.JSON;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by guohao on 2018/10/25.
 */
public class StorageObject {
    private int id;
    private String provider;
    private String bucketName;
    private String objectKey;
    private long size;
    private Date addTime;
    private Map<String, Object> metaData;
    private String metaDataString;
    private Date lastCheckTime;
    private int lastCheckStatus;
    private String fileMD5;
    private Date startTime;
    private Date expires;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public Date getAddTime() {
        return addTime;
    }

    public void setAddTime(Date addTime) {
        this.addTime = addTime;
    }

    public Map<String, Object> getMetaData() {
        return metaData;
    }

    public void setMetaData(Map<String, Object> metaData) {
        this.metaData = metaData;
        this.metaDataString = JSON.toJSONString(metaData);
    }

    public Date getLastCheckTime() {
        return lastCheckTime;
    }

    public void setLastCheckTime(Date lastCheckTime) {
        this.lastCheckTime = lastCheckTime;
    }

    public int getLastCheckStatus() {
        return lastCheckStatus;
    }

    public void setLastCheckStatus(int lastCheckStatus) {
        this.lastCheckStatus = lastCheckStatus;
    }

    public String getFileMD5() {
        return fileMD5;
    }

    public void setFileMD5(String fileMD5) {
        this.fileMD5 = fileMD5;
    }

    public Date getExpires() {
        return expires;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getMetaDataString() {
        return metaDataString;
    }

}

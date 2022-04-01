package org.hansk.tools.transfer.domain;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by guohao on 2018/5/17.
 */
public class Transfer {
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public long getObjectSize() {
        return objectSize;
    }

    public void setObjectSize(long objectSize) {
        this.objectSize = objectSize;
    }

    public String getTargetBucket() {
        return targetBucket == null || targetBucket.equals("")  || targetBucket.equals("null") ? bucket : targetBucket;
    }

    public void setTargetBucket(String targetBucket) {
        this.targetBucket = targetBucket;
    }

    public String getTargetProvider() {
        return targetProvider;
    }

    public void setTargetProvider(String targetProvider) {
        this.targetProvider = targetProvider;
    }

    public String getCdnDomain() {
        return cdnDomain;
    }

    public void setCdnDomain(String cdnDomain) {
        this.cdnDomain = cdnDomain;
    }

    public AtomicInteger getRetryTimes() {
        return retryTimes;
    }

    private int id;
    private String provider;
    private String object;
    private long objectSize;
    private String bucket;
    private String targetBucket;
    private String targetProvider;
    private int status;
    private String createTime;
    private String updateTime;
    private String cdnDomain;
    private AtomicInteger retryTimes = new AtomicInteger(0);
}

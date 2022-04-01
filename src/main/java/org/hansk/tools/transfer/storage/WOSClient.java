package org.hansk.tools.transfer.storage;

import com.baidubce.BceClientException;

import com.wos.services.WosClient;
import com.wos.services.WosConfiguration;
import com.wos.services.exception.WosException;
import com.wos.services.model.*;
import org.apache.commons.collections.map.HashedMap;
import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.domain.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;


@Component("WOS")
//@ConditionalOnProperty(prefix = "transfer", name = "transfer.wos.accessKey")
public class WOSClient implements IStorage{

    private static final int MultipartSize = 500 * 1024 * 1024;
    private static final int MultipartBlock = 8 * 1024 * 1024;
    
    private Logger logger = LoggerFactory.getLogger(WOSClient.class);

    private Config config;
    private Map<String, com.wos.services.WosClient> wosClients;

    @Autowired
    public WOSClient (Config config){
        this.config = config;
        wosClients = new HashedMap();
        Map<String, String> headers = new HashMap<>();
        WosConfiguration wosConfig = new WosConfiguration();
        wosConfig.setEndPoint(config.getWosEndPoint());
        wosConfig.setRegionName(config.getWosRegion());
        wosClients.put(config.getWosEndPoint(),new com.wos.services.WosClient(
                config.getWosAccessKey(), config.getWosSecretKey(), wosConfig));

        for (Config.Bucket bucket : config.getBuckets()) {
            if(bucket.getOriginEndPoint() !=null && !bucket.getOriginEndPoint().equals("")&& !wosClients.containsKey(bucket.getOriginEndPoint())){
                wosConfig.setRegionName(!bucket.getOriginRegion().equals("") ? bucket.getOriginRegion() : config.getWosRegion());
                WosClient downloader = new com.wos.services.WosClient(config.getWosAccessKey(), config.getWosSecretKey(),wosConfig);
                wosClients.put(bucket.getOriginBucket(),downloader);
            }
            if(bucket.getTargetEndPoint() != null && !bucket.getTargetEndPoint().equals("") && !wosClients.containsKey(bucket.getTargetEndPoint())){
                wosConfig.setRegionName(!bucket.getTargetRegion().equals("") ? bucket.getTargetRegion() : config.getWosRegion());
                com.wos.services.WosClient uploader = new com.wos.services.WosClient(config.getWosAccessKey(), config.getWosSecretKey(),wosConfig);
                wosClients.put(bucket.getTargetBucket(), uploader);
            }
        }

    }

    /**
     * 文件上传 网宿500MBi 必须分片
     * @param objStream 文件流
     * @param bucket 目标bucket
     * @param key object名称
     * @param objectSize
     * @param contentMD5 对象md5
     * @param metaData
     * @return
     * @throws Exception
     */
    @Override
    public boolean putObject(InputStream objStream, String bucket, String key, long objectSize, String contentMD5, Map<String, String> metaData) throws Exception {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(objectSize);
        com.wos.services.WosClient wosClient = wosClients.get(bucket);

        if(objectSize > MultipartSize ){
            try {
                ObjectMetadata metadata =  wosClient.getObjectMetadata(bucket,key);
                if(metadata != null && metadata.getContentLength() == objectSize) {
                    return true;
                }
            }catch (BceClientException ex ){
                logger.debug("file not exits, can upload");
            }
            return this.multiPartUpload(objStream, bucket, key, objectSize);
        }else {
            try{
                logger.info(String.format("pre sample upload cos, [bucket:%s key:%s size:%d]", bucket, key, objectSize));
                PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, key, objStream);
                PutObjectResult putObjectResult = wosClient.putObject(putObjectRequest);
                if(putObjectResult.getStatusCode() != 200) {
                    logger.error("wos upload fail, bucket:" + bucket + " key:"+ key);
                    return false;
                }
            }
            catch (WosException ex){
                if(ex.getErrorCode().equals("PathConflict")){
                    logger.warn("file exits," + "["+bucket + ":" + key +"]");
                    return true;
                }else{
                    throw ex;
                }
            }
        }

        return false;
    }

    @Override
    public StorageObject getObject(Transfer transfer) throws Exception {
        String bucket = transfer.getBucket();
        String objKey = transfer.getObject();
        //基于bucket设置Region
        StorageObject object = new StorageObject();
        WosObject wosObject = wosClients.get(config.getWosEndPoint()).getObject(bucket, objKey);
        object.content = wosObject.getObjectContent();
        object.setMetadata(new HashMap<>());
        object.getMetadata().put("Content-MD5",wosObject.getMetadata().getContentMd5()) ;
        object.getMetadata().put("Content-Type",wosObject.getMetadata().getContentType());
        object.getMetadata().put("Content-Length",wosObject.getMetadata().getContentLength());
        object.getMetadata().put("Last-Modified",wosObject.getMetadata().getLastModified());

        return object;
    }

    @Override
    public boolean isObjectExist(String bucket, String object) {
        com.wos.services.WosClient wosClient = wosClients.get(config.getCosRegion(bucket));
        return wosClient.doesObjectExist(bucket, object);
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    private boolean multiPartUpload(InputStream objStream, String bucket, String key, long objectSize) throws IOException {
        String uploadId ;
        WosClient wosClient = wosClients.get(config.getCosRegion(bucket));
        if(wosClient == null){
            logger.error("wos bucket have no own client", new Exception("no fucking client: bos, bucket: " +  bucket));
        }
        // 开始Multipart Upload
        InitiateMultipartUploadRequest initiateMultipartUploadRequest =
                new InitiateMultipartUploadRequest(bucket, key);
        InitiateMultipartUploadResult initiateMultipartUploadResult =
                wosClient.initiateMultipartUpload(initiateMultipartUploadRequest);
        uploadId = initiateMultipartUploadResult.getUploadId();
        //TODO WOS PartETag not impl!
        List<PartEtag> partETags =  new ArrayList<PartEtag>();
        int partCount = (int) (objectSize / MultipartBlock);
        if (objectSize % MultipartBlock != 0) {
            partCount++;
        }
        logger.info(String.format("pre multipart upload wos, [bucket:%s key:%s size:%d part:%d]", bucket, key, objectSize, partCount));
        for (int i = 0; i < partCount; i++) {
            long startPos = i * MultipartBlock;
            long curPartSize = (i + 1 == partCount) ? (objectSize - startPos) : MultipartBlock;
            // 跳过已经上传的分片。
            byte[] partBytes = new byte[(int) curPartSize];
            objStream.read(partBytes);
            InputStream partInputStream = new ByteArrayInputStream(partBytes);
//               objStream.skip(startPos);
            UploadPartRequest uploadPartRequest = new UploadPartRequest();
            uploadPartRequest.setBucketName(bucket);
            uploadPartRequest.setObjectKey(key);
            uploadPartRequest.setUploadId(uploadId);
            uploadPartRequest.setInput(partInputStream);

            uploadPartRequest.setPartSize(curPartSize);
            uploadPartRequest.setPartNumber( i + 1);
            try {
                UploadPartResult uploadPartResult = wosClient.uploadPart(uploadPartRequest);
                partETags.add(new PartEtag(uploadPartResult.getEtag(), uploadPartResult.getPartNumber()));
            }catch (Exception  ex){
                logger.error("分片上传失败" + ex.getMessage());
            }
        }
        Collections.sort(partETags, new Comparator<PartEtag>() {
            @Override
            public int compare(PartEtag p1, PartEtag p2) {
                return p1.getPartNumber() - p2.getPartNumber();
            }
        });
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(bucket, key, uploadId, partETags);
        CompleteMultipartUploadResult completeMultipartUploadResult = wosClient.completeMultipartUpload(completeMultipartUploadRequest);
        if(completeMultipartUploadResult.getLocation() !=null && !completeMultipartUploadResult.getLocation().isEmpty()){
            return true;
        }
        return false;
    }
}

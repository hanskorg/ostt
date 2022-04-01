package org.hansk.tools.transfer.storage;

import com.baidubce.BceClientException;
import com.baidubce.Region;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.bos.BosClient;
import com.baidubce.services.bos.BosClientConfiguration;
import com.baidubce.services.bos.model.*;

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

@Component("BOS")
//@ConditionalOnProperty(prefix = "transfer",  name = "transfer.bos.accessKeyID")
public class BOSClient  implements IStorage{

    private static final long MultipartSize = 500 * 1024 * 1024L;
    private static final long MultipartBlock = 5 * 1024 * 1024L;

    private Logger logger = LoggerFactory.getLogger(BOSClient.class);
    private Config config;
    private Map<String, com.baidubce.services.bos.BosClient> bosClients;

    @Autowired
    public BOSClient (Config config){
        if(config.getOssKey() == ""){
            return ;
        }
        this.config = config;
        bosClients = new HashedMap();
        Map<String, String> headers = new HashMap<>();
//        headers.put("referer", String.format("https://%s/",config.getOssAccessDomain()));
//        headers.put("Access-Control-Allow-Origin", String.format("https://%s/",config.getOssAccessDomain()));

        BosClientConfiguration bosConfig = new BosClientConfiguration();
        bosConfig.setCredentials(new DefaultBceCredentials(this.config.getBosAccessKeyId(), this.config.getBosSecretAccessKey()));
        bosConfig.setEndpoint(this.config.getBosEndpoint());

        if(!this.config.getBosEndpoint().equals("")) {
            bosClients.put(this.config.getBosEndpoint(),new com.baidubce.services.bos.BosClient(bosConfig));
        }

        for (Config.Bucket bucket : config.getBuckets()) {
            if(bucket.getOriginEndPoint() !=null && !bucket.getOriginEndPoint().equals("")&& !bosClients.containsKey(bucket.getOriginEndPoint())){
                BosClient downloader = new com.baidubce.services.bos.BosClient(bosConfig);
                bosClients.put(bucket.getOriginEndPoint(),downloader);
            }
            if(bucket.getTargetEndPoint() != null && !bucket.getTargetEndPoint().equals("") && !bosClients.containsKey(bucket.getTargetEndPoint())){
                com.baidubce.services.bos.BosClient uploader = new com.baidubce.services.bos.BosClient(bosConfig);
                bosClients.put(bucket.getTargetEndPoint(), uploader);
            }
        }

    }
    @Override
    public boolean putObject(InputStream objStream, String bucket, String key, long objectSize, String contentMD5, Map<String, String> metaData) throws Exception {
        boolean success = false;
        boolean isObjectExist = false;
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(objectSize);
//        if(objectSize > MultipartSize){
//            try {
//                isObjectExist =  bosClient.doesObjectExist(bucket,key);
//                if(isObjectExist){
//                    return true;
//                }
//            }catch (BceClientException ex ){
//                logger.error("file check fail, skip");
//                return false;
//            }
//
//        }

        if(objectSize < MultipartSize){
            BosClient bosClient = bosClients.get(config.getBosEndpoint(bucket));
            logger.info(String.format("pre sample upload bos, [bucket:%s key:%s size:%d]", bucket, key, objectSize));
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, key,
                    objStream,
                    objectMetadata
            );
            PutObjectResponse putObjectResult = bosClient.putObject(putObjectRequest);
            if(putObjectResult.getETag() != ""){
                success = true;
            }
        }else{
            logger.info(String.format("pre multipart upload bos, [bucket:%s key:%s size:%d]", bucket, key, objectSize));
            try {
                success =  this.multiPartUpload(objStream, bucket, key, objectSize);
            }catch (Exception ex){
                logger.error("upload bos fail, bucket: "+ bucket + " key: "+ key);
            }
        }
        return success;
    }

    @Override
    public StorageObject getObject(Transfer transfer) throws Exception {
        String bucket = transfer.getBucket();
        String objKey = transfer.getObject();
        //基于bucket设置Region
        StorageObject object = new StorageObject();
        BosObject bosObject = bosClients.get(config.getCosRegion(bucket)).getObject(bucket, objKey);
        object.content = bosObject.getObjectContent();
        object.setMetadata(new HashMap<>());
        object.getMetadata().put("Content-MD5",bosObject.getObjectMetadata().getContentMd5()) ;
        object.getMetadata().put("Content-Type",bosObject.getObjectMetadata().getContentType());
        object.getMetadata().put("Expires",bosObject.getObjectMetadata().getExpires());
        object.getMetadata().put("Content-Length",bosObject.getObjectMetadata().getContentLength());
        object.getMetadata().put("Last-Modified",bosObject.getObjectMetadata().getLastModified());

        return object;
    }

    @Override
    public boolean isObjectExist(String bucket, String object) {
        com.baidubce.services.bos.BosClient bosClient = bosClients.get(config.getCosRegion(bucket));
        return bosClient.doesObjectExist(bucket, object);
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    private boolean multiPartUpload(InputStream objStream, String bucket, String key, long objectSize) throws IOException {
        String uploadId ;
        BosClient bosClient = bosClients.get(config.getBosEndpoint(bucket));
        if(bosClient == null){
            logger.error("bucket have no own client", new Exception("no fucking client: bos, bucket: " +  bucket));
            throw new IOException("bos endpoint error");
        }
        // 开始Multipart Upload
        InitiateMultipartUploadRequest initiateMultipartUploadRequest = new InitiateMultipartUploadRequest(bucket, key);
        InitiateMultipartUploadResponse initiateMultipartUploadResponse = bosClient.initiateMultipartUpload(initiateMultipartUploadRequest);
        uploadId = initiateMultipartUploadResponse.getUploadId();
        List<PartETag> partETags =  new ArrayList<PartETag>();
        int partCount = (int) (objectSize / MultipartBlock);
        if (objectSize % MultipartBlock != 0) {
            partCount++;
        }
        logger.info(String.format("pre multipart upload bos, [bucket:%s key:%s size:%d part:%d]", bucket, key, objectSize, partCount));
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
            uploadPartRequest.setKey(key);
            uploadPartRequest.setUploadId(uploadId);
            uploadPartRequest.setInputStream(partInputStream);

            uploadPartRequest.setPartSize(curPartSize);
            uploadPartRequest.setPartNumber( i + 1);
            try {
                UploadPartResponse uploadPartResponse = bosClient.uploadPart(uploadPartRequest);
                // 每次上传分片之后，BOS的返回结果会包含一个PartETag。PartETag将被保存到partETags中。
                partETags.add(uploadPartResponse.getPartETag());
            }catch (Exception  ex){
                logger.error("分片上传失败" + ex.getMessage());
            }
        }
        Collections.sort(partETags, new Comparator<PartETag>() {
            @Override
            public int compare(PartETag p1, PartETag p2) {
                return p1.getPartNumber() - p2.getPartNumber();
            }
        });
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(bucket, key, uploadId, partETags);
        CompleteMultipartUploadResponse completeMultipartUploadResult = bosClient.completeMultipartUpload(completeMultipartUploadRequest);
        if(completeMultipartUploadResult.getLocation() !=null && !completeMultipartUploadResult.getLocation().isEmpty()){
            return true;
        }
        return false;
    }
}

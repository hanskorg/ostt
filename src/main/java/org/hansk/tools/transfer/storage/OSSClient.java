package org.hansk.tools.transfer.storage;

import com.aliyun.oss.model.*;
import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.domain.Transfer;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 阿里云存储客户端
 * @author guohao
 * @date 2018/10/16
 */
@Component("OSS")
@ConditionalOnProperty(prefix = "transfer", name = "transfer.oss.key")
public class OSSClient implements IStorage {

    private Config config;

    private Logger logger = LoggerFactory.getLogger(OSSClient.class);

    private Map<String, com.aliyun.oss.OSSClient> ossClients;


    @Autowired
    public OSSClient(Config config){
        this.config = config;
        this.ossClients = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        headers.put("referer", String.format("https://%s/",config.getOssAccessDomain()));
        headers.put("Access-Control-Allow-Origin", String.format("https://%s/",config.getOssAccessDomain()));

        ossClients.put(config.getOssEndPoint(),new com.aliyun.oss.OSSClient(config.getOssEndPoint(), config.getOssKey(), config.getOssSecret()));

        for (Config.Bucket bucket : config.getBuckets()) {
            if(bucket.getOriginEndPoint() !=null && !bucket.getOriginEndPoint().equals("")&& !ossClients.containsKey(bucket.getOriginEndPoint())){
                com.aliyun.oss.OSSClient downloader = new com.aliyun.oss.OSSClient(bucket.getOriginEndPoint(), config.getOssKey(), config.getOssSecret());
                downloader.getClientConfiguration().setDefaultHeaders(headers);
                ossClients.put(bucket.getOriginEndPoint(),downloader);
            }
            if(bucket.getTargetEndPoint() != null && !bucket.getTargetEndPoint().equals("") && !ossClients.containsKey(bucket.getTargetEndPoint())){
                com.aliyun.oss.OSSClient uploader = new com.aliyun.oss.OSSClient(bucket.getTargetEndPoint(), config.getOssKey(), config.getOssSecret());
                uploader.getClientConfiguration().setDefaultHeaders(headers);
                ossClients.put(bucket.getTargetEndPoint(), uploader);
            }
        }
    }
    @Override
    public boolean putObject(InputStream objStream, String bucket, String key, long objectSize, String contentMD5, Map<String, String> metaData) throws Exception {
        boolean isSuccess = false;
        com.aliyun.oss.OSSClient ossClient = ossClients.get(config.getOssEndPoint(bucket));
        //200M以下简单传输
        if(objectSize < 200 * 1024 * 1024L){
            try{
                logger.info(String.format("pre sample upload oss, [bucket:%s key:%s size:%d]", bucket, key, objectSize));
                com.aliyun.oss.model.PutObjectResult result = ossClient.putObject( bucket, key, objStream);
                if(result.getResponse() != null && result.getResponse().isSuccessful()){
                    isSuccess = true;
                }
                if(result.getResponse() == null){
                    isSuccess = ossClient.doesObjectExist(bucket,key);
                    logger.info(String.format("oss file exist, [bucket:%s key:%s size:%d]", bucket, key, objectSize ));
                }
            }catch (Exception ex){
                logger.error("OSS上传失败:" + ex.toString());
            }

        }else{
            if(ossClient.doesObjectExist(bucket,key)){
                ObjectMetadata objectMetadata = ossClient.getObjectMetadata(bucket, key);
                if(objectMetadata.getContentLength() == objectSize){
                    isSuccess = true;
                    logger.info(String.format("oss file exist, [bucket:%s key:%s size:%d]", bucket, key, objectSize ));
                }else{
                    //TODO ..待优化
                    isSuccess = true;
                    logger.info(String.format("oss file exist, but size not match [bucket:%s key:%s size:%d, exist_size:%d]", bucket, key, objectSize, objectMetadata.getContentLength() ));
                }
            }

            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucket, key);
            InitiateMultipartUploadResult result = ossClient.initiateMultipartUpload(request);
            String uploadId = result.getUploadId();
            List<PartETag> partETags =  new ArrayList<PartETag>();
            // 1MB
            final long partSize = 1 * 1024 * 1024L;
            int partCount = (int) (objectSize / partSize);
            if (objectSize % partSize != 0) {
                partCount++;
            }
            logger.info(String.format("pre multipart upload oss, [bucket:%s key:%s size:%d part:%d]", bucket, key, objectSize, partCount));
            for (int i = 0; i < partCount; i++) {
                long startPos = i * partSize;
                long curPartSize = (i + 1 == partCount) ? (objectSize - startPos) : partSize;
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

                // 设置分片大小。除了最后一个分片没有大小限制，其他的分片最小为100KB。
                uploadPartRequest.setPartSize(curPartSize);
                // 设置分片号。每一个上传的分片都有一个分片号，取值范围是1~10000，如果超出这个范围，OSS将返回InvalidArgument的错误码。
                uploadPartRequest.setPartNumber( i + 1);
                // 每个分片不需要按顺序上传，甚至可以在不同客户端上传，OSS会按照分片号排序组成完整的文件。
                try {
                    UploadPartResult uploadPartResult = ossClient.uploadPart(uploadPartRequest);
                    // 每次上传分片之后，OSS的返回结果会包含一个PartETag。PartETag将被保存到partETags中。
                    partETags.add(uploadPartResult.getPartETag());
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
            CompleteMultipartUploadResult completeMultipartUploadResult = ossClient.completeMultipartUpload(completeMultipartUploadRequest);
            if(completeMultipartUploadResult.getLocation() !=null && !completeMultipartUploadResult.getLocation().isEmpty()){
                isSuccess = true;
            }
        }
        return isSuccess;
    }

    // 首先是将流缓存到byteArrayOutputStream中
    public void inputStreamCacher(ByteArrayOutputStream byteArrayOutputStream, InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) > -1 ) {
            byteArrayOutputStream.write(buffer, 0, len);
        }
    }

    @Override
    public StorageObject getObject(Transfer transfer) throws Exception {
        String bucket = transfer.getBucket();
        String objKey = transfer.getObject();
        StorageObject object = new StorageObject();
        com.aliyun.oss.OSSClient ossClient = ossClients.get(config.getOssEndPoint(bucket));

        OSSObject ossObject = ossClient.getObject(new GetObjectRequest(bucket, objKey));
        object.content = ossObject.getObjectContent();
        object.setMetadata(new HashMap<>());
        object.getMetadata().put("Content-MD5",ossObject.getObjectMetadata().getContentMD5()) ;
        object.getMetadata().put("Content-Type",ossObject.getObjectMetadata().getContentType());
        object.getMetadata().put("Content-Length",ossObject.getObjectMetadata().getContentLength());
        object.getMetadata().put("Last-Modified", ossObject.getObjectMetadata().getLastModified());
        return object;
    }

    @Override
    public boolean isObjectExist(String bucket, String object) {
        return true;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

}

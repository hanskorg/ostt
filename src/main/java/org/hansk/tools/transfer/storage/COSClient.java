package org.hansk.tools.transfer.storage;

import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.domain.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 腾讯云存储客户端
 * @author guohao
 * @date 2018/10/16
 */
@Component("COS")
//@ConditionalOnProperty(prefix = "transfer", name = "transfer.cos.secretId")
public class COSClient implements IStorage {

    private Config config;

    private Logger logger = LoggerFactory.getLogger(COSClient.class);

    private Map<String, com.qcloud.cos.COSClient> cosClients;


    @Autowired
    public COSClient(Config config){
        this.config = config;
        cosClients = new HashMap<>();
        COSCredentials cred = new BasicCOSCredentials(config.getCosSecretID(), config.getCosSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(config.getCosRegion()));
        cosClients.put(config.getCosRegion(), new com.qcloud.cos.COSClient(cred, clientConfig));
        for (Config.Bucket bucket : config.getBuckets()) {
            if(bucket.getOriginRegion() !=null && !bucket.getOriginRegion().equals("") && !cosClients.containsKey(bucket.getOriginRegion())){
                ClientConfig cConfig = new ClientConfig(new Region(bucket.getOriginRegion()));
                cosClients.put(bucket.getOriginRegion(), new com.qcloud.cos.COSClient(cred, cConfig));
            }
            if(bucket.getTargetRegion() !=null && !bucket.getTargetRegion().equals("") && !cosClients.containsKey(bucket.getTargetRegion())){
                ClientConfig cConfig = new ClientConfig(new Region(bucket.getTargetRegion()));
                cosClients.put(bucket.getTargetRegion(), new com.qcloud.cos.COSClient(cred, cConfig));
            }
        }
    }
    @Override
    public boolean putObject(InputStream objStream, String bucket, String key, long objectSize, String contentMD5, Map<String, String> metaData) throws Exception {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(objectSize);
        com.qcloud.cos.COSClient cosClient = cosClients.get(config.getCosRegion(bucket));
        String parrtern = ".*-(125|100|20)[0-9]{3,}$";
        if (!Pattern.matches(parrtern, bucket)){
            bucket = bucket + "-" + config.getCosAppID();
        }
        if(objectSize > 200 * 1024 * 1024 ){
            try {
                ObjectMetadata metadata =  cosClient.getObjectMetadata(bucket,key);
                if(metadata != null && metadata.getContentLength() == objectSize) {
                    return true;
                }
            }catch (CosServiceException ex){
                if (ex.getStatusCode() == 404 ){
                    logger.debug("file not exits, can upload");
                }
            }
        }
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, key,
                objStream,
                objectMetadata
        );
        try{
            logger.info(String.format("pre sample upload cos, [bucket:%s key:%s size:%d]", bucket, key, objectSize));
            PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
        }catch (CosServiceException ex){
            if(ex.getErrorCode().equals("PathConflict")){
                logger.warn("file exits," + "["+bucket + ":" + key +"]");
                return true;
            }else{
                throw ex;
            }
        }
        return true;
    }

    @Override
    public boolean isObjectExist(String bucket, String object) {
        com.qcloud.cos.COSClient cosClient = cosClients.get(config.getCosRegion(bucket));
        return cosClient.doesObjectExist(bucket, object);
    }

    @Override
    public StorageObject getObject(Transfer transfer) throws Exception {
        String bucket = transfer.getBucket();
        String objKey = transfer.getObject();
        //基于bucket设置Region
        StorageObject object = new StorageObject();
        COSObject cosObject = cosClients.get(config.getCosRegion(bucket)).getObject(bucket, objKey);
        object.content = cosObject.getObjectContent();
        object.setMetadata(new HashMap<>());
        object.getMetadata().put("Content-MD5",cosObject.getObjectMetadata().getContentMD5()) ;
        object.getMetadata().put("Content-Type",cosObject.getObjectMetadata().getContentType());
        object.getMetadata().put("Expires",cosObject.getObjectMetadata().getExpirationTime());
        object.getMetadata().put("Content-Length",cosObject.getObjectMetadata().getContentLength());
        object.getMetadata().put("Last-Modified",cosObject.getObjectMetadata().getLastModified());

        return object;
    }

    public void setConfig(Config config) {
        this.config = config;
    }
}

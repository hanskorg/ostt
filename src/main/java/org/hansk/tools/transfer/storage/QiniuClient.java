package org.hansk.tools.transfer.storage;

import com.aliyun.oss.common.utils.IOUtils;
import com.qiniu.common.Zone;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.BucketInfo;
import com.qiniu.util.Auth;
import org.bouncycastle.util.Strings;
import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.domain.Transfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 七牛存储客户端
 * @author guohao
 * @date 2018/10/16
 */
@Component("QINIU")
//@ConditionalOnProperty(prefix = "transfer", name = "transfer.qiniu.accessKey")
public class QiniuClient implements IStorage {

    private BucketManager bucketManager;
    private Auth auth;
    private UploadManager uploadManager;

    private Config config;
    private Map<String, String[]> domains;
    private Map<String, BucketInfo> bucketsInfo;
    @Autowired
    public QiniuClient(Config config){

        this.config = config;
        this.domains = new HashMap<>();
        this.bucketsInfo = new HashMap<>();
        auth = Auth.create(config.getQiniuAccess(), config.getQiniuSecret());
        Configuration cfg = new Configuration(Zone.zone1());
        uploadManager = new UploadManager(cfg);
        bucketManager = new BucketManager(auth,cfg);
    }
    @Override
    public boolean putObject(InputStream objStream, String bucket, String key, long objectSize, String contentMD5, Map<String, String> metaData) throws Exception {
        String upToken = auth.uploadToken(bucket, key);
        Response resp = uploadManager.put(IOUtils.readStreamAsByteArray(objStream), key, upToken);
        return resp.isOK();
    }

    @Override
    public StorageObject getObject(Transfer transfer) throws Exception {
        String bucket = transfer.getBucket();
        String objKey = transfer.getObject();
        StorageObject object = new StorageObject();
        String domain = transfer.getCdnDomain();
        String fileUrl;
        if(domain == null || domain.equals("")){
            if(!domains.containsKey(bucket)){
                String[] domainList = bucketManager.domainList(bucket);
                domains.put(bucket, domainList);
            }
            String[] bucketDomains =  domains.get(bucket);
            domain = bucketDomains[(int)Math.floor(Math.random() * bucketDomains.length)];
        }
        if(Strings.toLowerCase(domain).startsWith("http") || Strings.toLowerCase(domain).startsWith("https")){
            fileUrl = domain + "/" + objKey;
        }else{
            fileUrl = "http://"+domain + "/" + objKey;
        }
        if(!bucketsInfo.containsKey(bucket)){
            bucketsInfo.put(bucket, bucketManager.getBucketInfo(bucket));
        }
        BucketInfo bucketInfo = bucketsInfo.get(bucket);
        if (bucketInfo.getPrivate() == 1){
            fileUrl = auth.privateDownloadUrl(fileUrl);
        }

        URL url = new URL(fileUrl);
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(0);
        urlConnection.setReadTimeout(0);
        try{
            object.content = urlConnection.getInputStream();
            object.setMetadata(new HashMap<>());
            object.getMetadata().put("Content-Length", urlConnection.getContentLengthLong());
            object.getMetadata().put("Content-Type",urlConnection.getContentType());
            object.getMetadata().put("Last-Modified",new Date(urlConnection.getLastModified()));

            return object;
        }catch (FileNotFoundException exception){
            throw new TransferException(TransferException.ErrorType.NotFound, "file not Found", exception);
        }
    }
    @Override
    public boolean isObjectExist(String bucket, String object) {
        return true;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    private String specialSign(String objectKey){
        String key = "ac9b46abb8883e98aeb8e998b588129a1ea989a9";
        Integer timeStamp = (int)(System.currentTimeMillis() / 1000);
        String timestampHex = Integer.toHexString(timeStamp);
        String newObjectKey = objectKey;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(("/" + key + objectKey + timestampHex).getBytes());
            byte[] secretBytes = md.digest();
            String encodeStr = new BigInteger(1, secretBytes).toString(16);
            for (int i = 0; i < 32 - encodeStr.length(); i++) {
                encodeStr = "0" + encodeStr;
            }
            newObjectKey = String.format("%s?sign=%s&t=%s", objectKey, Strings.toLowerCase(encodeStr), timestampHex);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return newObjectKey;

    }
}

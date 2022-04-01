package org.hansk.tools.transfer.dao;

import org.apache.ibatis.annotations.Param;
import org.hansk.tools.transfer.domain.StorageObject;

import java.util.Date;
import java.util.List;

/**
 * Created by guohao on 2018/10/25.
 */
public interface ObjectsMapper {

    public List<StorageObject> findByStatus(@Param("provider")String provider, @Param("bucket")String bucket,@Param("status")int status, @Param("start")int start, @Param("limit")int limit);
    public List<StorageObject> findObject(@Param("provider")String provider, @Param("bucket")String bucket, @Param("objectKey")String objectKey, @Param("start")int start, @Param("limit")int limit);
    public int addObject(@Param("provider")String provider
            ,@Param("bucket")String bucket
            ,@Param("objectKey")String objectKey
            ,@Param("fileMD5")String fileMD5
            ,@Param("expires")Date expires
            ,@Param("lastCheckStatus")int lastCheckStatus
            ,@Param("startTime")Date startTime
            ,@Param("status")int lastCheckTime
    );

    public boolean insert(@Param("record") StorageObject object );
    public boolean insertAll(@Param("recordList") List<StorageObject> objectList);
    public int updateStatus(@Param("id")int id, @Param("status")int status);

}
package org.hansk.tools.transfer.dao;

import org.apache.ibatis.annotations.Param;

/**
 * Created by guohao on 2018/5/18.
 */
public interface OptionsMapper {
    public String getValue(@Param("option_key")String optionKey);
    public boolean setValue(@Param("option_key")String optionKey, @Param("option_value")String optionValue);

}

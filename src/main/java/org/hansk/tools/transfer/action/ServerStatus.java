package org.hansk.tools.transfer.action;

import org.hansk.tools.transfer.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Created by guohao on 2018/5/21.
 */
public class ServerStatus implements ApplicationRunner {

    @Autowired
    private Config config;
    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {
        config.setStatus(Config.Status.RUNNING);
    }

    public void setConfig(Config config) {
        this.config = config;
    }
}

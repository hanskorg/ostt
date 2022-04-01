package org.hansk.tools;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.region.Region;
import org.hansk.tools.transfer.Config;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
public class OsstransferApplicationTests {
	public void setConfig(Config config) {
		this.config = config;
	}

	@Autowired
	private Config config;
	@Test
	public void config(){
		System.out.println(config);
	}
	@Test
	public void contextLoads() {

	}

}

package org.hansk.tools;
import com.aliyun.oss.OSSClient;
import org.hansk.tools.transfer.Config;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
public class TransferApplicationTests {
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
	public void ossBandwidth() {
		OSSClient ossClient = null;
		ossClient.getBucketStat("test");
	}

}

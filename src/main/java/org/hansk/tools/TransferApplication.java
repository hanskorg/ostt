package org.hansk.tools;

import org.hansk.tools.transfer.Config;
import org.hansk.tools.transfer.action.*;
import javafx.scene.Parent;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

@SpringBootApplication
@EnableAutoConfiguration
@MapperScan("org.hansk.tools.transfer.dao")
public class TransferApplication {

	private static Logger logger = LoggerFactory.getLogger(TransferApplication.class);

	public static void main(String[] args) {
		ConfigurableApplicationContext configurableApplicationContext =  new SpringApplicationBuilder()
				.sources(Parent.class)
				.child(TransferApplication.class)
				.child(FetchCosObjectRunner.class)
				.child(FetchOssObjectRunner.class)
				.child(FetchQiniuObjectRunner.class)
				.child(FetchWosObjectRunner.class)
				.child(FetchBosObjectRunner.class)
				.child(TransferObjectRunner.class)
				.child(CheckIsExistsRunner.class)
				.run();
		configurableApplicationContext.getBean(Config.class).setStatus(Config.Status.STARTING);

		configurableApplicationContext.addApplicationListener(new ApplicationListener<ContextClosedEvent>() {

			@Override
			public void onApplicationEvent(ContextClosedEvent contextClosedEvent) {
				TransferApplication.logger.info("shudowning");
				configurableApplicationContext.getBean(Config.class).setStatus(Config.Status.SHUTTING);
				configurableApplicationContext.getBean(TransferObjectRunner.class).getScheduledThreadPoolExecutor().shutdown();
				configurableApplicationContext.getBean(CheckIsExistsRunner.class).getScheduledThreadPoolExecutor().shutdown();
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		});
	}
}

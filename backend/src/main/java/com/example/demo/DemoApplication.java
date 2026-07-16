package com.example.demo;

import com.example.demo.config.AnalyticsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DemoApplication {

	private static final Logger log = LoggerFactory.getLogger(DemoApplication.class);

	public static void main(String[] args) {
		ConfigurableApplicationContext ctx = SpringApplication.run(DemoApplication.class, args);
		// 诊断：直接打印 LLM API key 是否被加载（仅打印长度，不打印密钥值）
		AnalyticsConfig cfg = ctx.getBean(AnalyticsConfig.class);
		String key = cfg.getApiKey();
		log.info("[启动诊断] LLM 配置：apiUrl={}, apiKey长度={}, model={}",
				cfg.getApiUrl(),
				key == null ? 0 : key.length(),
				cfg.getModel());
		if (key == null || key.isBlank()) {
			log.warn("[启动诊断] ⚠️ DEEPSEEK_API_KEY 未配置，改进建议生成将回退到规则模板。请配置环境变量或 application.properties。");
		}
	}

}

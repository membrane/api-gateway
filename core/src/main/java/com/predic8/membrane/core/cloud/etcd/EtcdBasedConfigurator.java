package com.predic8.membrane.core.cloud.etcd;

import java.io.IOException;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class EtcdBasedConfigurator implements ApplicationContextAware, Lifecycle {

	private ApplicationContext context;

	@Override
	public boolean isRunning() {
		return false;
	}

	@Override
	public void start() {
		// move to EtcdBasedConfigurer
		Router router = context.getBean(Router.class);
		for (int i = 0; i < 2; i++) { // TODO: loop over modules
			int port = 8080;
			String path = "eep"; // TODO: read from etcd, identisch mit
									// modulname
			String targetHost = "localhost"; // TODO: read from etcd
			int targetPort = 8081; // TODO: read from etcd
			ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("*", "*", path, port), targetHost, targetPort);
			try {
				router.getRuleManager().addProxyAndOpenPortIfNew(sp2);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void stop() {

	}

	@Override
	public void setApplicationContext(ApplicationContext arg0) throws BeansException {
		context = arg0;
	}
}
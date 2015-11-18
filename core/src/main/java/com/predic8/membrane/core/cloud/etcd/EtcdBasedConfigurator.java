package com.predic8.membrane.core.cloud.etcd;

import java.io.IOException;
import java.util.ArrayList;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

import com.fasterxml.jackson.core.JsonParseException;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

@MCElement(name = "etcdBasedConfigurator")
public class EtcdBasedConfigurator implements ApplicationContextAware, Lifecycle {

	private ApplicationContext context;
	private int port = 8080;
	private String baseUrl;
	private String baseKey;
	private Router router = new HttpRouter();

	public int getPort() {
		return port;
	}

	/**
	 * @description port
	 * @default 8080
	 */
	@MCAttribute
	public void setPort(int port) {
		this.port = port;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	/**
	 * @description URL for etcd
	 * @default "http://localhost:4001"
	 */
	@MCAttribute
	public void setBaseUrl(String baseURL) {
		this.baseUrl = baseURL;
	}

	public String getBaseKey() {
		return baseKey;
	}

	/**
	 * @description Key/Directory
	 * @default "/asa/lb"
	 */
	@MCAttribute
	public void setBaseKey(String baseKey) {
		this.baseKey = baseKey;
	}

	@Override
	public boolean isRunning() {
		return false;
	}

	@Override
	public void start() {
		router = context.getBean(Router.class);
		EtcdResponse respAvailableModules = EtcdUtil.createBasicRequest(baseUrl, baseKey, "").sendRequest();
		if (!EtcdUtil.checkOK(respAvailableModules)) {
			throw new RuntimeException();
		}
		ArrayList<String> availableModules = respAvailableModules.getDirectories();
		System.out.println("EtcdBasedConfigurator OUTPUT:");
		for (String module : availableModules) {
			EtcdResponse respAvailableServicesForModule = EtcdUtil.createBasicRequest(baseUrl, baseKey, module)
					.sendRequest();
			if (!EtcdUtil.checkOK(respAvailableServicesForModule)) {
				throw new RuntimeException();
			}
			ArrayList<String> availableUUIDs = respAvailableServicesForModule.getDirectories();
			System.out.println("Module: " + module);
			for (String uuid : availableUUIDs) {

				EtcdResponse respName = EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
						.getValue("name").sendRequest();
				if (!EtcdUtil.checkOK(respName)) {
					throw new RuntimeException();
				}
				String targetName = respName.getValue();

				EtcdResponse respPort = EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
						.getValue("port").sendRequest();
				if (!EtcdUtil.checkOK(respPort)) {
					throw new RuntimeException();
				}
				int targetPort = Integer.parseInt(respPort.getValue());

				EtcdResponse respHost = EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
						.getValue("host").sendRequest();
				if (!EtcdUtil.checkOK(respHost)) {
					throw new RuntimeException();
				}
				String targetHost = respHost.getValue();
				System.out.println("Information for " + uuid + ":");
				System.out.println("Name: " + targetName);
				System.out.println("Host: " + targetHost);
				System.out.println("Port: " + targetPort);
				setUpServiceProxy(module, this.port, targetHost, targetPort);
			}
			System.out.println("---");
		}
		try {
			// router.init();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void setUpServiceProxy(String path, int port, String targetHost, int targetPort) {
		ServiceProxy sp = new ServiceProxy(new ServiceProxyKey("*", "*", path, port), targetHost, targetPort);
		try {
			router.add(sp);
			// router.getRuleManager().addProxyAndOpenPortIfNew(sp);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
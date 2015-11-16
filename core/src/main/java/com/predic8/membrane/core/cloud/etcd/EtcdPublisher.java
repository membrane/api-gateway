package com.predic8.membrane.core.cloud.etcd;

import java.util.HashSet;
import java.util.UUID;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.transport.http.HttpClient;

@MCElement(name = "etcdPublisher")
public class EtcdPublisher implements ApplicationContextAware, Lifecycle {

	private ApplicationContext context;
	private String myUUID = UUID.randomUUID().toString();
	private HashSet<String> uuidsOfProxies = new HashSet<String>();
	private int ttlInSeconds = 300;
	boolean isRunning = true;

	private Thread ttlRefreshThread = new Thread(new Runnable() {

		boolean isFirstTime = true;

		@Override
		public void run() {
			if (isFirstTime) {
				isFirstTime = false;
				try {
					Thread.sleep((ttlInSeconds - 10) * 1000);
				} catch (InterruptedException e) {
				}
			}
			while (isRunning) {
				System.out.println("Refreshing ttl");
				for (String uuid : uuidsOfProxies) {
					EtcdResponse respTTLDirRefresh = new EtcdRequest().local().defaultBaseModule().defaultModule()
							.uuid(uuid).refreshTTL(ttlInSeconds).sendRequest();
					if (!isInRange(200, 300, respTTLDirRefresh.getOriginalResponse().getStatusCode())) {
						throw new RuntimeException();
					}
				}
				try {
					Thread.sleep((ttlInSeconds - 10) * 1000);
				} catch (InterruptedException e) {
				}
			}
		}

	});

	@Override
	public boolean isRunning() {
		return false;
	}

	private boolean isInRange(int minInclusive, int maxExclusive, int value) {
		return value >= minInclusive && value < maxExclusive;
	}

	@Override
	public void start() {
		Router router = context.getBean(Router.class);
		for (Rule rule : router.getRuleManager().getRules()) {
			if (rule instanceof ServiceProxy) {
				ServiceProxy sp = (ServiceProxy) rule;

				if (sp.getPath() == null) {
					continue;
				}
				String path = sp.getPath().getValue();
				String name = sp.getName();
				String port = Integer.toString(sp.getPort());
				String uuid = UUID.randomUUID().toString();

				EtcdResponse respTTLDirCreate = new EtcdRequest().local().defaultBaseModule().defaultModule()
						.createDir(uuid).ttl(ttlInSeconds).sendRequest();
				if (!isInRange(200, 300, respTTLDirCreate.getOriginalResponse().getStatusCode())) {
					throw new RuntimeException();
				}

				EtcdResponse respName = new EtcdRequest().local().defaultBaseModule().defaultModule().uuid(uuid)
						.setValue("name", name).sendRequest();
				if (!isInRange(200, 300, respName.getOriginalResponse().getStatusCode())) {
					System.out.println(respName.getOriginalResponse().getBodyAsStringDecoded());
					throw new RuntimeException();
				}

				EtcdResponse respPort = new EtcdRequest().local().defaultBaseModule().defaultModule().uuid(uuid)
						.setValue("port", port).sendRequest();
				if (!isInRange(200, 300, respPort.getOriginalResponse().getStatusCode())) {
					System.out.println(respPort.getOriginalResponse().getBodyAsStringDecoded());
					throw new RuntimeException();
				}

				EtcdResponse respPath = new EtcdRequest().local().defaultBaseModule().defaultModule().uuid(uuid)
						.setValue("path", path).sendRequest();
				if (!isInRange(200, 300, respPath.getOriginalResponse().getStatusCode())) {
					System.out.println(respPath.getOriginalResponse().getBodyAsStringDecoded());
					throw new RuntimeException();
				}

				uuidsOfProxies.add(uuid);
			}
		}
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				isRunning = false;
				ttlRefreshThread.interrupt();
				for (String uuid : uuidsOfProxies) {
					EtcdResponse respUnregisterProxy = new EtcdRequest().local().defaultBaseModule().defaultModule()
							.uuid(uuid).deleteDir().sendRequest();
					// this is probably unneeded as the etcd data has ttl set
					// and will autodelete after the ttl
				}
			}
		});
		ttlRefreshThread.start();

	}

	@Override
	public void stop() {

	}

	@Override
	public void setApplicationContext(ApplicationContext arg0) throws BeansException {
		context = arg0;

	}

}

package com.predic8.membrane.core.cloud.etcd;

public class EtcdUtil {
	private static boolean isInRange(int minInclusive, int maxExclusive, int value) {
		return value >= minInclusive && value < maxExclusive;
	}

	public static EtcdRequest createBasicRequest(String url, String baseKey, String module) {
		return new EtcdRequest().url(url).baseKey(baseKey).module(module);
	}

	public static boolean checkStatusCode(int minInc, int maxExc, EtcdResponse resp) {
		if (!isInRange(minInc, maxExc, resp.getOriginalResponse().getStatusCode())) {
			return false;
		}
		return true;
	}

	public static boolean checkOK(EtcdResponse resp) {
		return checkStatusCode(200, 300, resp);
	}
}

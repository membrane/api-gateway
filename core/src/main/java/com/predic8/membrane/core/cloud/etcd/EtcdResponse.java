package com.predic8.membrane.core.cloud.etcd;

import com.predic8.membrane.core.http.Response;

public class EtcdResponse {

	private EtcdRequest originalRequest;
	private Response originalResponse;

	public EtcdRequest getOriginalRequest() {
		return originalRequest;
	}

	public void setOriginalRequest(EtcdRequest originalRequest) {
		this.originalRequest = originalRequest;
	}

	public Response getOriginalResponse() {
		return originalResponse;
	}

	public void setOriginalResponse(Response originalResponse) {
		this.originalResponse = originalResponse;
	}

	public EtcdResponse(EtcdRequest originalRequest, Response resp) {
		this.originalRequest = originalRequest;
		originalResponse = resp;

		// TODO process the originalResponse
	}
}

package com.predic8.membrane.core.interceptor.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdmissionReview {
    private String apiVersion;
    private String kind;
    private AdmissionRequest request;
    private AdmissionResponse response;

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public AdmissionRequest getRequest() {
        return request;
    }

    public void setRequest(AdmissionRequest request) {
        this.request = request;
    }

    public AdmissionResponse getResponse() {
        return response;
    }

    public void setResponse(AdmissionResponse response) {
        this.response = response;
    }
}

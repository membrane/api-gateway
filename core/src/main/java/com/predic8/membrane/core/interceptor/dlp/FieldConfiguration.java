package com.predic8.membrane.core.interceptor.dlp;

import java.util.Map;

public interface FieldConfiguration {
    Map<String, String> getFields(String fileName);
}

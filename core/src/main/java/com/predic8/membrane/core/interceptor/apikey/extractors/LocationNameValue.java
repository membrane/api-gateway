package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.security.*;

public record LocationNameValue(ApiKeySecurityScheme.In location, String name, String key) {}

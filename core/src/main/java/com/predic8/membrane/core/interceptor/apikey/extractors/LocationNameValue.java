package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.http.Request.*;

public record LocationNameValue(In location, String name, String key) {}

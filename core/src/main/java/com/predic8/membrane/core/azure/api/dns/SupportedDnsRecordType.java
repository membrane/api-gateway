package com.predic8.membrane.core.azure.api.dns;

import java.util.List;
import java.util.Map;

public interface SupportedDnsRecordType {
    Map<String, List<Map<String, List<String>>>> payload();
}

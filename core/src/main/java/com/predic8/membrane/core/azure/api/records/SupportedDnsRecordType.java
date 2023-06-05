package com.predic8.membrane.core.azure.api.records;

import java.util.List;
import java.util.Map;

public interface SupportedDnsRecordType {
    Map<String, Map<String, List<String>>> payload();
}

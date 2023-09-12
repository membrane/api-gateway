/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.azure.api.dns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TxtRecordBuilder implements SupportedDnsRecordType {

    private final DnsRecordCommandExecutor parent;
    private final List<String> values = new ArrayList<>();

    public TxtRecordBuilder(DnsRecordCommandExecutor parent) {
        this.parent = parent;
    }

    public DnsRecordCommandExecutor withValue(String... values) {
        Collections.addAll(this.values, values);
        return parent;
    }

    @Override
    public Map<String, List<Map<String, List<String>>>> payload() {
        return Map.of(
                "TXTRecords", List.of(
                        Collections.singletonMap("value", List.of(String.join("", values)))
                )
        );
    }
}

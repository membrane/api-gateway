/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.openapi.validators.*;

import java.util.concurrent.*;

public class ValidationStatisticsCollector {

    /**
     * Map<Status Code, StatisticCollector>
     */
    private final ConcurrentHashMap<ValidationStatsKey, Integer> stats = new ConcurrentHashMap<>();

    public ConcurrentHashMap<ValidationStatsKey, Integer> getStats() {
        return stats;
    }

    public void collect(ValidationErrors vc) {
        vc.stream().forEach(validationError -> {
            // synchronized not needed because merge impl is atomic.
            stats.merge(new ValidationStatsKey(validationError.getValidationContext()), 1, (prev, one) -> prev + 1);
        });
    }
}

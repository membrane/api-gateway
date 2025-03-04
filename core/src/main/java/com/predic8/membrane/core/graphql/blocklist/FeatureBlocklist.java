/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.graphql.blocklist;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.graphql.GraphQLOverHttpValidationException;
import com.predic8.membrane.core.graphql.blocklist.filters.GraphQLFeatureFilter;
import com.predic8.membrane.core.graphql.model.ExecutableDocument;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@MCElement(name = "disallow")
public class FeatureBlocklist {

    private List<GraphQLFeatureFilter> filters;

    public void checkFilters(@NotNull ExecutableDocument document) throws GraphQLOverHttpValidationException {
        filters.forEach(f -> f.filter(document));
    }

    @MCChildElement(allowForeign = true)
    public void setFilters(List<GraphQLFeatureFilter> filters) {
        this.filters = filters;
    }

    public List<GraphQLFeatureFilter> getFilters() {
        return filters;
    }
}
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

package com.predic8.membrane.annot.yaml;

import org.yaml.snakeyaml.error.*;

/**
 * Public wrapper for SnakeYAML's MarkedYAMLException that exposes the protected constructor
 * for use by YAML parsing components in the Kubernetes integration.
 * <p>
 * This exception provides detailed error context including source marks for both
 * the context and problem locations in YAML files.
 */
public class PublicMarkedYAMLException extends MarkedYAMLException {
    protected PublicMarkedYAMLException(String context, Mark contextMark, String problem, Mark problemMark, String note) {
        super(context, contextMark, problem, problemMark, note);
    }
}

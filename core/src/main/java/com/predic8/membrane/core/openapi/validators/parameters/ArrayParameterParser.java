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

package com.predic8.membrane.core.openapi.validators.parameters;

import java.util.*;
import java.util.stream.*;

public class ArrayParameterParser extends AbstractArrayParameterParser {

    protected Stream<String> getItems() {

        var value = values.get(parameter.getName());
        if (value == null || value.isEmpty()) {
            return Stream.empty();
        }

        String[] items = value.getFirst().split(",");
        if (items.length == 0) {
            return Stream.empty();
        }
        if (items.length == 1) {
            if (items[0].equals("null")) {
                return null;
            }
            // foo= => foo: "" => Let assume an empty parameter is an empty array
            if (items[0].isEmpty()) {
                return Stream.empty();
            }
        }
        return Arrays.stream(items);
    }

}

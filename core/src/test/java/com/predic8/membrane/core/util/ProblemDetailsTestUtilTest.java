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

package com.predic8.membrane.core.util;

import tools.jackson.databind.core.*;
import com.predic8.membrane.core.exceptions.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProblemDetailsTestUtilTest {

    @Test
    void parse() throws JsonProcessingException {
        ProblemDetails pd = ProblemDetailsTestUtil.parse(ProblemDetails.user(false, "a")
                .addSubType("validation")
                .status(421)
                .title("Validation error")
                .detail("Wrong format")
                .build());

        assertEquals(421, pd.getStatus());
        assertEquals("https://membrane-api.io/problems/user/validation", pd.getType());
    }
}
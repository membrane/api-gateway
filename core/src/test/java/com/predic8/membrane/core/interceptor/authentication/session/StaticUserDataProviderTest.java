/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.authentication.session;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.registration.SecurityUtils.isHashedPassword;

public class StaticUserDataProviderTest {
    @Test
    public void test() {
        Assertions.assertTrue(
                isHashedPassword("$5$9d3c06e19528aebb$cZBA3E3SdoUvk865.WyPA5iNUEA7uwDlDX7D5Npkh8/"));
        Assertions.assertTrue(
                isHashedPassword("$5$99a6391616158b48$PqFPn9f/ojYdRcu.TVsdKeeRHKwbWApdEypn6wlUQn5"));

    }
}

/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

public class OAuth2MembraneExampleTest extends DistributionExtractingTestcase {

    @Test
    public void test() throws Exception {
        try(Process2 ignored = new Process2.Builder().in(getExampleDir("oauth2/membrane/authorization_server")).script("membrane") .waitForMembrane()
                .start()) {

            try(Process2 ignored2 = new Process2.Builder().in(getExampleDir("oauth2/membrane/client")).script("membrane").waitForMembrane().start();
                HttpAssertions ha = new HttpAssertions()) {
                    // note that we just check that both servers come up and roughly work. this does not follow the
                    // README.txt .
                    String[] headers = new String[2];
                    headers[0] = "Content-Type";
                    headers[1] = "application/x-www-form-urlencoded";
                    ha.getAndAssert(200, "http://localhost:2000/login/");
                    ha.postAndAssert(307, "http://localhost:8000/oauth2/auth?client_id=abc&response_type=code&scope=openid%20profile&redirect_uri=http://localhost:2000/oauth2callback&state=security_token=37la4hu9jjdmou5c0minb7tll2&url=/login/", headers, "target=&username=john&password=password");
            }
        }
    }

    @Override
    protected String getExampleDirName() {
        return "oauth2/membrane";
    }
}

/*
 * Copyright 2015 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.apimanagement;

import com.predic8.membrane.core.interceptor.apimanagement.policy.Policy;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class ApiManagementConfigurationTest {

    @Test
    public void testParseYaml() throws Exception {
        String source =  new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir") + "\\src\\test\\resources\\apimanagement\\api.yaml")), Charset.defaultCharset());

        ApiManagementConfiguration conf = new ApiManagementConfiguration();
        conf.setLocation(source);
        Map<String, Policy> policies = conf.getPolicies();
        for(Policy p : policies.values()){
            System.out.println(p);
        }
        Map<String, Key> keys = conf.getKeys();
        for(Key k : keys.values()){
            System.out.println(k);
        }

    }
}

/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.cloud.etcd;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class EtcdRequestTest {

    @Test
    public void testFillEtcdWithYaml() throws IOException {
        String source =  new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir") + "\\src\\test\\resources\\apimanagement\\api.yaml")), Charset.defaultCharset());

        try {
            EtcdResponse respPutYaml = EtcdUtil.createBasicRequest("http://localhost:4001", "/amc", "").setValue("file", source).sendRequest();
            EtcdResponse respPutHash = EtcdUtil.createBasicRequest("http://localhost:4001", "/amc", "").setValue("hash", "12345").sendRequest();
        }catch(Exception ignored){
        }



    }

}

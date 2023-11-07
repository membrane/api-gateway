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
package com.predic8.membrane.core.interceptor.acl;

import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.Router;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParseTypeTest {

    static ParseType pt;

    static final String blobSchema = "192.168.0.*";
    static final String regexSchema = "192.168.0.(7|8)";
    static final String cidrSchema = "192.168.0.0/20";

    @Test
    public void matchesBlobSchema() throws UnknownHostException{
        assertEquals(true, pt.GLOB.getMatcher().matches("192.168.0.1", blobSchema));
    }

    @Test
    public void notMatchesBlobSchema() throws UnknownHostException{
        assertEquals(false, pt.GLOB.getMatcher().matches("192.168.1.1", blobSchema));
    }

    @Test
    public void matchesRegexSchema() throws UnknownHostException{
        assertEquals(true, pt.REGEX.getMatcher().matches("192.168.0.8", regexSchema));
    }

    @Test
    public void notMatchesRegexSchema() throws UnknownHostException{
        assertEquals(false, pt.REGEX.getMatcher().matches("192.168.0.9", regexSchema));
    }

    @Test
    public void matchesCidrSchema() throws UnknownHostException{
        assertEquals(true, pt.CIDR.getMatcher().matches("192.168.15.254", cidrSchema));
    }

    @Test
    public void notMatchesCidrSchema() throws UnknownHostException{
        assertEquals(false, pt.CIDR.getMatcher().matches("192.168.16.254", cidrSchema));
    }

}

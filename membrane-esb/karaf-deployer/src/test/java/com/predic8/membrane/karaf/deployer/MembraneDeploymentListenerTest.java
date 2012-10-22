/**
 * Copyright 2012 predic8 GmbH, www.predic8.com
 * Copyright original authors of SpringDeploymentListenerTest.java
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.predic8.membrane.karaf.deployer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.jar.JarInputStream;

import com.predic8.membrane.karaf.deployer.MembraneTransformer;

import junit.framework.TestCase;

public class MembraneDeploymentListenerTest extends TestCase {

    public void testCustomManifest() throws Exception {
        File f = File.createTempFile("smx", ".jar");
        try {
            OutputStream os = new FileOutputStream(f);
            MembraneTransformer.transform(getClass().getClassLoader().getResource("proxies.xml"), os);
            os.close();
            InputStream is = new FileInputStream(f);
            JarInputStream jar = new JarInputStream(is);
            jar.getManifest().write(System.err);
            jar.close();
            is.close();
        } finally {
            f.delete();
        }
    }

}

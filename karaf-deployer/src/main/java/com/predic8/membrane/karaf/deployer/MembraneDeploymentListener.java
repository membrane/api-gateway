/**
 * Copyright 2012 predic8 GmbH, www.predic8.com
 * Copyright original authors of SpringDeploymentListener.java
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
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.felix.fileinstall.ArtifactUrlTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * A deployment listener that listens for Membrane xml applications
 * and creates bundles for these.
 */
public class MembraneDeploymentListener implements ArtifactUrlTransformer {

    private final Logger logger = LoggerFactory.getLogger(MembraneDeploymentListener.class);

    private DocumentBuilderFactory dbf;
    
    public boolean canHandle(File artifact) {
        try {
            if (artifact.isFile() && artifact.getName().endsWith(".xml")) {
                Document doc = parse(artifact);
                String name = doc.getDocumentElement().getLocalName();
                String uri  = doc.getDocumentElement().getNamespaceURI();
                logger.warn(name + " " + uri);
                if ("proxies".equals(name) && "http://membrane-soa.org/proxies/1/".equals(uri)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Unable to parse deployed file " + artifact.getAbsolutePath(), e);
        }
        return false;
    }

    public URL transform(URL artifact) {
        try {
            return new URL("membrane", null, artifact.toString());
        } catch (Exception e) {
            logger.error("Unable to build Membrane application bundle", e);
            return null;
        }
    }

    protected Document parse(File artifact) throws Exception {
        if (dbf == null) {
            dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
        }
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException exception) throws SAXException {
            }
            public void error(SAXParseException exception) throws SAXException {
            }
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return db.parse(artifact);
    }

}

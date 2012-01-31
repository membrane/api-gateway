package com.predic8.membrane.core;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;

/**
 * 
 * Fixes Issue with StAX RI parser, that doesn't correctly lookup attribute values with getAttributeValue when one uses null or "" as namespace parameter. 
 * That Issue is fixed since update 18.
 */
public class FixedStreamReader extends StreamReaderDelegate {
    public FixedStreamReader(XMLStreamReader rdr) {
        super(rdr);
    }

    @Override
    public String getAttributeValue(String ns, String lName) {
        if (!"".equals(ns) && ns != null) {
            return super.getAttributeValue(ns, lName);
        }

        for (int i = 0; i < getAttributeCount(); i++) {
            if ( (ns == null || getAttributeNamespace(i) == null) && lName.equals(getAttributeLocalName(i))) {
                return getAttributeValue(i);
            }
        }
        return null;
    }
} 
package com.predic8.membrane.core.util.text;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import org.slf4j.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.core.util.xml.NormalizeXMLForJsonUtil.*;

public class ToJsonSerializer {

    private static final Logger log = LoggerFactory.getLogger(ToJsonSerializer.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String toJson(Object o) {
         try {
            if (o instanceof NodeList || o instanceof Node) {
                o = normalizeForJson(o);
            }
            if (o instanceof NodeList nl) {
                return nodeListToJson(nl);
            }
            return objectMapper.writeValueAsString(o);
        } catch (Exception first) {
            // Fallback: always return valid JSON, even for unsupported types (e.g. java.time.* without modules).
            log.debug("Failed to convert object to JSON. Falling back to JSON string.", first);
            try {
                return objectMapper.writeValueAsString(String.valueOf(o));
            } catch (Exception fallback) {
                log.info("Failed to convert fallback value to JSON.", fallback);
                return "null";
            }
        }
    }

    public static String nodeListToJson(NodeList nl) throws JsonProcessingException {
        var values = new ArrayList<String>(nl.getLength());
        for (int i = 0; i < nl.getLength(); i++) {
            values.add(String.valueOf(nl.item(i).getTextContent()));
        }
        return objectMapper.writeValueAsString(values);
    }
}

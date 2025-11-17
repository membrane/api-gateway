package com.predic8.membrane.core.cli.util;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.networknt.schema.InputFormat;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.schemavalidation.json.JSONYAMLSchemaValidator;
import com.predic8.membrane.core.kubernetes.BeanCache;
import com.predic8.membrane.core.kubernetes.client.WatchAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import static com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION;
import static com.fasterxml.jackson.dataformat.yaml.YAMLFactory.builder;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.schemavalidation.json.JSONYAMLSchemaValidator.SCHEMA_VERSION_2020_12;
import static java.nio.file.Files.readString;
import static java.util.UUID.randomUUID;

public class YamlLoader {
    private static final Logger log = LoggerFactory.getLogger(YamlLoader.class);

    private static final ObjectMapper om = new ObjectMapper();

    public static void sendYamlToBeanCache(Router router, String location, BeanCache beanCache) throws Exception {
        validate(router, location);

        final YAMLFactory yamlFactory = builder().enable(STRICT_DUPLICATE_DETECTION).build();

        try (YAMLParser parser = yamlFactory.createParser(new File(location))) {
            int count = 0;

            while (!parser.isClosed()) {
                Map<String,Object> m = om.readValue(parser, Map.class);
                if (m == null) {
                    log.debug("Skipping empty document. Maybe there are two --- separators but no configuration in between.");
                    parser.nextToken();
                    continue;
                }

                count++;
                fillMissingFields(location, m, count);

                beanCache.handle(WatchAction.ADDED, m);
                parser.nextToken();
            }

            beanCache.fireConfigurationLoaded();
        } catch (JsonParseException e) {
            throw new IOException(
                    "Invalid YAML: multiple configurations must be separated by '---' "
                            + "(at line " + e.getLocation().getLineNr()
                            + ", column " + e.getLocation().getColumnNr() + ").",
                    e
            );
        }
    }

    private static void fillMissingFields(String location, Map<String, Object> m, int count) {
        Map<String, Object> meta = (Map<String, Object>) m.get("metadata");
        if (meta == null) {
            // generate name, if it doesnt exist
            meta = new TreeMap<>();
            m.put("metadata", meta);
            meta.put("name", "artifact" + count);
            meta.put("uid", randomUUID().toString());
        } else {
            // fake UID
            meta.put("uid", location + "-" + meta.get("name"));
        }
    }

    private static void validate(Router router, String location) throws Exception {
        var configExchange = post("http://localhost/config")
                .body(readString(new File(location).toPath()))
                .buildExchange();
        var validator = new JSONYAMLSchemaValidator(
                router.getResolverMap(),
                "classpath:/com/predic8/membrane/core/config/json/membrane.schema.json",
                (message, exc) -> {
                    log.error(message);
                },
                SCHEMA_VERSION_2020_12,
                InputFormat.YAML
        );
        validator.init();
        if (validator.validateMessage(configExchange, Interceptor.Flow.REQUEST) == ABORT)
            System.exit(1);
    }
}

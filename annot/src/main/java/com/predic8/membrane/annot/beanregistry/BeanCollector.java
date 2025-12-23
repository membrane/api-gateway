package com.predic8.membrane.annot.beanregistry;

import com.predic8.membrane.annot.Grammar;
import com.predic8.membrane.annot.yaml.GenericYamlParser;
import com.predic8.membrane.annot.yaml.WatchAction;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * This is the definition side of a {@link BeanRegistryImplementation}. You can start the bean registry
 * and send it a series of change events.
 */
public interface BeanCollector {
    /**
     * Utility method to ingest a stream of YAML objects as a static configuration and then
     * start the bean registry.
     * @param yamls stream of YAML objects
     * @param grammar the grammar to use for parsing
     */
    default void parseYamls(InputStream yamls, Grammar grammar) throws IOException {
        List<BeanDefinition> bds = GenericYamlParser.parseMembraneResources(yamls, grammar);
        for (int i = 0; i < bds.size(); i++) {
            handle(new BeanDefinitionChanged(WatchAction.ADDED, bds.get(i)), i == bds.size() - 1);
        }
        handle(new StaticConfigurationLoaded(), true);
        start();
    }

    /**
     * @param changeEvent the change event
     * @param isLast indicates whether this is the last change event for this batch of changes
     */
    void handle(ChangeEvent changeEvent, boolean isLast);

    /**
     * Starts the bean registry.
     */
    void start();
}

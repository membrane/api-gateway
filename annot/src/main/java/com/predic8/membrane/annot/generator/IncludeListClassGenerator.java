package com.predic8.membrane.annot.generator;

import javax.annotation.processing.ProcessingEnvironment;

public class IncludeListClassGenerator extends ClassGenerator {

    public IncludeListClassGenerator(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    @Override
    protected String getClassName() {
        return "IncludeList";
    }

    @Override
    protected String getClassImpl() {
        return """
                import com.predic8.membrane.annot.MCChildElement;
                import com.predic8.membrane.annot.MCElement;
                
                import java.util.ArrayList;
                import java.util.List;
                
                /**
                 * @description <p>
                 * Includes additional YAML configuration files before parsing the current file's own configuration.
                 * </p>
                 * <p>
                 * Include entries are resolved in the order they are listed. If an entry points to a directory,
                 * all matching <code>*.apis.yaml</code> / <code>*.apis.yml</code> files in that directory are included.
                 * For directory includes, no ordering is applied. Includes are resolved recursively and cyclic
                 * include chains are rejected.
                 * </p>
                 * <p>
                 * Paths used inside included configuration content (for example OpenAPI file locations or other
                 * referenced resources) are resolved against the base path of the main configuration file.
                 * </p>
                 * @yaml <pre><code>
                 * include:
                 *   - "."
                 *   - apis/demo.apis.yaml
                 *   - other/apis
                 * </code></pre>
                 */
                @MCElement(name = "include", topLevel = true, noEnvelope = true, component = false)
                public class IncludeList {
                
                    List<String> includes = new ArrayList<>();
                
                    /**
                     * @description <p>
                     * Declares include entries as a list of strings.
                     * </p>
                     * <p>
                     * Each string is a path to either a YAML file or a directory. Relative paths are resolved
                     * against the directory of the including file. Absolute paths are also supported.
                     * </p>
                     */
                    @MCChildElement(allowForeign = true)
                    public void setInclude(List<String> include) {
                        this.includes = include;
                    }
                
                    public List<String> getInclude() {
                        return includes;
                    }
                
                }
                """;
    }

}

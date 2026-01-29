/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.yaml.spel;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;

import static com.predic8.membrane.annot.yaml.spel.EnvPropertyAccessor.EnvVariables.INSTANCE;
import static java.lang.System.getenv;

public final class EnvPropertyAccessor implements PropertyAccessor {

    private static final Logger LOG = LoggerFactory.getLogger(EnvPropertyAccessor.class);

    @Override
    public Class<?>[] getSpecificTargetClasses() {
        return new Class<?>[]{SpELContext.class, EnvVariables.class};
    }

    @Override
    public boolean canRead(@NotNull EvaluationContext context, Object target, @NotNull String name) {
        if (target instanceof SpELContext) return "env".equals(name);
        return target instanceof EnvVariables;
    }

    @Override
    public @NotNull TypedValue read(@NotNull EvaluationContext context, Object target, @NotNull String name) throws AccessException {
        if (target instanceof SpELContext) {
            return new TypedValue(INSTANCE);
        }
        if (target instanceof EnvVariables) {
            String value = getenv(name);
            if (value == null) {
                LOG.warn("Environment variable '{}' not found. Note: env lookups are case-sensitive.", name);
            }
            return new TypedValue(value);
        }
        throw new AccessException("Unsupported target: " + target);
    }

    @Override
    public boolean canWrite(@NotNull EvaluationContext context, Object target, @NotNull String name) {
        return false;
    }

    @Override
    public void write(@NotNull EvaluationContext context, Object target, @NotNull String name, Object newValue) throws AccessException {
        throw new AccessException("Writing is not supported.");
    }

    public static final class EnvVariables {
        static final EnvVariables INSTANCE = new EnvVariables();
        private EnvVariables() {}
    }
}

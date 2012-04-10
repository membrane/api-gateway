/*
 * Copyright 2006-2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.osgi.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Dictionary;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;
import org.springframework.util.ReflectionUtils.FieldFilter;

/**
 * Utility class for OSGi {@link Bundle}s. Provides convenience methods for
 * interacting with a Bundle object.
 * 
 * @author Adrian Colyer
 * @author Costin Leau
 * @see OsgiStringUtils
 */
public abstract class OsgiBundleUtils {

	/**
	 * Whether Bundle#getBundleContext() method is available (even on 4.0
	 * platforms)
	 */
	private static final boolean getBundleContextAvailable;

	private static volatile BundleContextExtractor extractor;

	static {
		getBundleContextAvailable = (ReflectionUtils.findMethod(Bundle.class, "getBundleContext", new Class[0]) != null);
		if (getBundleContextAvailable)
			extractor = new Osgi41BundleContextExtractor();
	}


	private interface BundleContextExtractor {

		BundleContext getBundleContext(Bundle bundle);
	}

	private static class Osgi41BundleContextExtractor implements BundleContextExtractor {

		public BundleContext getBundleContext(Bundle bundle) {
			return bundle.getBundleContext();
		}
	}

	private static class ReflectionMethodInvocation implements BundleContextExtractor {

		private final Method method;


		private ReflectionMethodInvocation(Method method) {
			ReflectionUtils.makeAccessible(method);
			this.method = method;
		}

		public BundleContext getBundleContext(Bundle bundle) {
			return (BundleContext) ReflectionUtils.invokeMethod(method, bundle);
		}
	}

	private static class FieldExtractor implements BundleContextExtractor {

		private final Field field;


		private FieldExtractor(Field field) {
			ReflectionUtils.makeAccessible(field);
			this.field = field;
		}

		public BundleContext getBundleContext(Bundle bundle) {
			return (BundleContext) ReflectionUtils.getField(field, bundle);
		}
	}


	/**
	 * Returns the underlying BundleContext for the given Bundle. This uses
	 * reflection and highly dependent of the OSGi implementation. Should not be
	 * used if OSGi 4.1 is being used.
	 * 
	 * @param bundle OSGi bundle
	 * @return the bundle context for this bundle
	 */
	public static BundleContext getBundleContext(final Bundle bundle) {
		if (bundle == null)
			return null;

		if (extractor == null) {
			// try getBundleContext (for non OSGi 4.1 platforms)
			Method method = ReflectionUtils.findMethod(bundle.getClass(), "getBundleContext", new Class[0]);
			if (method != null) {
				if (Modifier.isPublic(method.getModifiers())) {
					extractor = new Osgi41BundleContextExtractor();
				}
			}
			else {
				if (method == null) {
					// try Equinox getContext
					method = ReflectionUtils.findMethod(bundle.getClass(), "getContext", new Class[0]);
				}
				if (method != null) {
					extractor = new ReflectionMethodInvocation(method);
				}
				else {
					final Field[] fields = new Field[1];
					// fallback to field inspection (KF and Prosyst)
					ReflectionUtils.doWithFields(bundle.getClass(), new FieldCallback() {

						public void doWith(final Field field) throws IllegalArgumentException, IllegalAccessException {
							ReflectionUtils.makeAccessible(field);
							fields[0] = field;
						}
					}, new FieldFilter() {

						public boolean matches(Field field) {
							return fields[0] == null && BundleContext.class.isAssignableFrom(field.getType());
						}
					});

					if (fields[0] != null) {
						extractor = new FieldExtractor(fields[0]);
					}
					else {
						throw new IllegalArgumentException("Cannot extract bundleContext from bundle type "
								+ bundle.getClass());
					}
				}
			}
		}

		return extractor.getBundleContext(bundle);
	}

	/**
	 * Indicates if the given bundle is active or not.
	 * 
	 * @param bundle OSGi bundle
	 * @return true if the given bundle is active, false otherwise
	 * @see Bundle#ACTIVE
	 */
	public static boolean isBundleActive(Bundle bundle) {
		Assert.notNull(bundle, "bundle is required");
		return (bundle.getState() == Bundle.ACTIVE);
	}

	/**
	 * Indicates if the given bundle is resolved or not.
	 * 
	 * @param bundle OSGi bundle
	 * @return true if the given bundle is resolved, false otherwise
	 * @see Bundle#RESOLVED
	 */
	public static boolean isBundleResolved(Bundle bundle) {
		Assert.notNull(bundle, "bundle is required");
		return (bundle.getState() >= Bundle.RESOLVED);
	}

	/**
	 * Indicates if the given bundle is lazily activated or not. That is, the
	 * bundle has a lazy activation policy and a STARTING state. Bundles that
	 * have been lazily started but have been activated will return false.
	 * 
	 * <p/>
	 * On OSGi R4.0 platforms, this method will always return false.
	 * 
	 * @param bundle OSGi bundle
	 * @return true if the bundle is lazily activated, false otherwise.
	 */
	@SuppressWarnings("unchecked")
	public static boolean isBundleLazyActivated(Bundle bundle) {
		Assert.notNull(bundle, "bundle is required");

		if (OsgiPlatformDetector.isR41()) {
			if (bundle.getState() == Bundle.STARTING) {
				Dictionary<Object, Object> headers = bundle.getHeaders();
				if (headers != null) {
					Object val = headers.get(/*Constants.BUNDLE_ACTIVATIONPOLICY*/ "Bundle-ActivationPolicy");
					if (val instanceof String) {
						String value = ((String) val).trim();
						return (value.startsWith(/*Constants.ACTIVATION_LAZY*/ "lazy"));
					}
				}
			}
		}
		return false;
	}

	/**
	 * Indicates if the given bundle is a fragment or not.
	 * 
	 * @param bundle OSGi bundle
	 * @return true if the given bundle is a fragment, false otherwise
	 * @see Constants#FRAGMENT_HOST
	 */
	public static boolean isFragment(Bundle bundle) {
		Assert.notNull(bundle, "bundle is required");
		return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
	}

	/**
	 * Indicates if the given bundle is the system bundle or not.
	 * 
	 * @param bundle OSGi bundle
	 * @return true if the given bundle is a fragment, false otherwise
	 */
	public static boolean isSystemBundle(Bundle bundle) {
		Assert.notNull(bundle);
		return (bundle.getBundleId() == 0);
	}

	/**
	 * Returns the given bundle version.
	 * 
	 * @param bundle OSGi bundle
	 * @return bundle version
	 * @see Constants#BUNDLE_VERSION
	 */
	public static Version getBundleVersion(Bundle bundle) {
		return getHeaderAsVersion(bundle, Constants.BUNDLE_VERSION);
	}

	/**
	 * Finds an install bundle based by its symbolic name.
	 * 
	 * @param bundleContext OSGi bundle context
	 * @param symbolicName bundle symbolic name
	 * @return bundle matching the symbolic name (<code>null</code> if none is
	 *         found)
	 */
	public static Bundle findBundleBySymbolicName(BundleContext bundleContext, String symbolicName) {
		Assert.notNull(bundleContext, "bundleContext is required");
		Assert.hasText(symbolicName, "a not-null/not-empty symbolicName isrequired");

		Bundle[] bundles = bundleContext.getBundles();
		for (int i = 0; i < bundles.length; i++) {
			if (symbolicName.equals(bundles[i].getSymbolicName())) {
				return bundles[i];
			}
		}
		return null;
	}

	/**
	 * Returns the version for a given bundle manifest header.
	 * 
	 * @param bundle OSGi bundle
	 * @param header bundle manifest header
	 * @return the header value
	 */
	public static Version getHeaderAsVersion(Bundle bundle, String header) {
		Assert.notNull(bundle);
		return Version.parseVersion((String) bundle.getHeaders().get(header));
	}
}
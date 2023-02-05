/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.config.spring;

import java.io.*;

public interface CheckableBeanFactory {

	/**
	 * Checks whether this application context's configuration file(s) contain valid bean definitions.
	 * @throws InvalidConfigurationException if the configuration is not valid
	 */
	void checkForInvalidBeanDefinitions() throws InvalidConfigurationException;

	class InvalidConfigurationException extends Exception {

		@Serial
		private static final long serialVersionUID = 1L;

		public InvalidConfigurationException() {
			super();
		}

		public InvalidConfigurationException(String message, Throwable cause) {
			super(message, cause);
		}

		public InvalidConfigurationException(String message) {
			super(message);
		}

		public InvalidConfigurationException(Throwable cause) {
			super(cause);
		}

	}
}

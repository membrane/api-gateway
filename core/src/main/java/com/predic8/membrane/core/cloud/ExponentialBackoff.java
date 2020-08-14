/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExponentialBackoff {
	private static final Logger LOG = LoggerFactory.getLogger(ExponentialBackoff.class);

	public interface Job {
		boolean run() throws Exception;
	}

	public static void retryAfter(long initialDelay, long maxDelay, double factor, String jobDescription, Job job)
			throws InterruptedException {
		long delay = initialDelay;

		while (true) {
			Exception f = null;
			try {
				if (job.run())
					break;
			} catch (InterruptedException e) {
				throw e;
			} catch (Exception e) {
				f = e;
			}
			if (f != null)
				LOG.error(jobDescription + " failed. Retrying in " + (delay / 1000) + "s.", f);
			else
				LOG.info(jobDescription + " failed. Retrying in " + (delay / 1000) + "s.");
			Thread.sleep(delay);
			delay = Math.min(maxDelay, (long) (delay * factor));
		}
		LOG.debug(jobDescription + " succeeded.");
	}
}

/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util;

public class DateUtil {
	public static String prettyPrintTimeSpan(long timespan) {
		timespan = timespan / 1000;

		long sec = (timespan >= 60 ? timespan % 60 : timespan);
		long min = (timespan = (timespan / 60)) >= 60 ? timespan % 60 : timespan;
		long hrs = (timespan = (timespan / 60)) >= 24 ? timespan % 24 : timespan;
		long day = (timespan = (timespan / 24));

		StringBuilder sb = new StringBuilder(50);
		if (day != 0)
			sb.append(String.format("%d d, ", day));
		if (day != 0 || hrs != 0)
			sb.append(String.format("%d h, ", hrs));
		if (day != 0 || hrs != 0 || min != 0)
			sb.append(String.format("%d m, ", min));
		sb.append(String.format("%d s", sec));
		return sb.toString();
	}
}

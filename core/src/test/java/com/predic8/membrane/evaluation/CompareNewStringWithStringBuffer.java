/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.evaluation;


public class CompareNewStringWithStringBuffer {

	public static void main(String[] args) {
		char[] buff = "--------Hallo World------".toCharArray();

		System.out.println(new String(buff, 8, 11));

		long time = System.currentTimeMillis();
		for (int i = 0; i < 1000000; i++) {
			new String(buff, 8, 11);
		}
		System.out.println("time new string: "
				+ (System.currentTimeMillis() - time) / 1000.0);

		time = System.currentTimeMillis();
		for (int i = 0; i < 1000000; i++) {
			StringBuffer sBuf = new StringBuffer();
			sBuf.append(buff, 8, 11);
			sBuf.toString();
		}
		System.out.println("time string buffer append char[]: "
				+ (System.currentTimeMillis() - time) / 1000.0);

		time = System.currentTimeMillis();
		for (int i = 0; i < 1000000; i++) {
			StringBuffer sBuf = new StringBuffer();
			for (int j = 8; j < 19; j++) {
				sBuf.append(buff[j]);
			}
			sBuf.toString();
		}
		System.out.println("time string buffer append one char: "
				+ (System.currentTimeMillis() - time) / 1000.0);
	}
}
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
package com.predic8.membrane.core.interceptor.authentication.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamReader;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.interceptor.authentication.session.CleanupThread.Cleaner;

/**
 * @description Keeps track of blocked user accounts (accounts become blocked after too many failed logins).
 * @explanation <p>
 *              The Account Blocker prevents password and token guessing attempts: It blocks a user (or the whole
 *              system) after too many failed login attempts.
 *              </p>
 *              <p>
 *              When a user entered a wrong password or wrong token more than <i>afterFailedLogins</i> times, this user
 *              becomes blocked: He will be prevented from logging in again within the next <i>blockFor</i> milliseconds
 *              (writing 3600000 means "for 1 hour").
 *              </p>
 *              <p>
 *              The failed login attempts have to occur within the last <i>afterFailedLoginsWithin</i> milliseconds
 *              (writing 9223372036854775807 means "forever").
 *              </p>
 *              <p>
 *              If more than <i>blockWholeSystemAfter</i> users become blocked at a time, the <i>all</i> users will
 *              become blocked. (This is necessary to limit memory usage.)
 *              </p>
 *              <h3>Discussion</h3>
 *              <p>
 *              Say, for example, a scripted dictionary attack tries to guess a user's password. Using the configuration
 *              shown above, which is the default configuration if no <i>accountBlocker</i> is declared, this results in
 *              5 guesses per 3600000 milliseconds; or equivalently 42720 guesses per year.
 *              </p>
 *              <p>
 *              The probability of hitting a uniformly at random chosen word of the standard German vocabulary within
 *              one year is therefore about 56%.
 *              </p>
 *              <p>
 *              Therefore, a more secure password should be chosen, containing letters, digits and special characters.
 *              </p>
 */
@MCElement(name="accountBlocker")
public class AccountBlocker extends AbstractXmlElement implements Cleaner {
	private static Logger log = LoggerFactory.getLogger(AccountBlocker.class.getName());

	private int blockWholeSystemAfter = 1000000;
	private int afterFailedLogins = 5;
	private long afterFailedLoginsWithin = Long.MAX_VALUE;
	private long blockFor = 3600000;

	private HashMap<String, Info> users = new HashMap<>();

	private class Info {
		private final long tries[];
		private int current = 0;
		private long blockedUntil;

		public Info() {
			tries = new long[afterFailedLogins-1];
		}

		public synchronized boolean isBlocked() {
			if (blockedUntil == 0)
				return false;
			return System.currentTimeMillis() < blockedUntil;
		}

		private synchronized void fail() {
			current = ++current % tries.length;
			long firstFail = tries[current];
			long now = tries[current] = System.currentTimeMillis();
			if (firstFail == 0)
				return;
			if (now - firstFail < afterFailedLoginsWithin)
				blockedUntil = now + blockFor;
		}

		public synchronized boolean hasRelevantInformation(long death) {
			return tries[current] > death;
		}
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		blockWholeSystemAfter = Integer.parseInt(StringUtils.defaultString(token.getAttributeValue("", "blockWholeSystemAfter"), "1000000"));
		afterFailedLogins = Integer.parseInt(StringUtils.defaultString(token.getAttributeValue("", "afterFailedLogins"), "5"));
		afterFailedLoginsWithin = Long.parseLong(StringUtils.defaultString(token.getAttributeValue("", "afterFailedLoginsWithin"), ""+Long.MAX_VALUE));
		blockFor = Integer.parseInt(StringUtils.defaultString(token.getAttributeValue("", "blockFor"), "3600000"));
	}

	public boolean isBlocked(String username) {
		Info info;
		synchronized (users) {
			if (users.size() == blockWholeSystemAfter) {
				log.error("There are " + blockWholeSystemAfter + " blocked user accounts. To avoid on OutOfMemoryError all accounts have been blocked.");
				return true;
			}
			info = users.get(username);
		}
		if (info == null)
			return false;
		return info.isBlocked();
	}

	public void unblock(String username) {
		synchronized (users) {
			users.remove(username);
		}
	}

	public boolean fail(String username) {
		Info info, info2 = new Info();
		synchronized (users) {
			info = users.get(username);
			if (info == null) {
				info = info2;
				if (users.size() < blockWholeSystemAfter)
					users.put(username, info);
			}
		}
		info.fail();
		return info.isBlocked();
	}

	public void cleanup() {
		List<String> removeUs = new ArrayList<>();
		long death = System.currentTimeMillis() - afterFailedLoginsWithin;
		synchronized (users) {
			for (Map.Entry<String, Info> e : users.entrySet())
				if (!e.getValue().hasRelevantInformation(death))
					removeUs.add(e.getKey());
			for (String username : removeUs)
				users.remove(username);
		}
	}

	public int getBlockWholeSystemAfter() {
		return blockWholeSystemAfter;
	}

	@MCAttribute
	public void setBlockWholeSystemAfter(int blockWholeSystemAfter) {
		this.blockWholeSystemAfter = blockWholeSystemAfter;
	}

	public int getAfterFailedLogins() {
		return afterFailedLogins;
	}

	@MCAttribute
	public void setAfterFailedLogins(int afterFailedLogins) {
		this.afterFailedLogins = afterFailedLogins;
	}

	public long getAfterFailedLoginsWithin() {
		return afterFailedLoginsWithin;
	}

	@MCAttribute
	public void setAfterFailedLoginsWithin(long afterFailedLoginsWithin) {
		this.afterFailedLoginsWithin = afterFailedLoginsWithin;
	}

	public long getBlockFor() {
		return blockFor;
	}

	@MCAttribute
	public void setBlockFor(long blockFor) {
		this.blockFor = blockFor;
	}
}

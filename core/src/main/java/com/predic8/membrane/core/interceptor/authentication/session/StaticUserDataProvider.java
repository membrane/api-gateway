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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;
import com.predic8.membrane.core.Router;
import org.apache.commons.codec.digest.Crypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @description A <i>user data provider</i> listing all user data in-place in the config file.
 * @explanation <p>
 *              the <i>staticuserdataprovider</i> can be used to statically list user data within the config file.
 *              </p>
 *              <p>
 *              each user must have a unique <i>username</i> attribute as well as a <i>password</i> attribute.
 *              </p>
 *              <p>
 *              arbitrary attributes can be set on a user element. other sub-components of the <i>login</i> interceptor
 *              might use those: for example, the <i>telekomsmstokenprovider</i> uses the <i>sms</i> property as the
 *              user's cell phone number. for example, the <i>totptokenprovider</i> uses the <i>secret</i> property to
 *              initialize the token sequence.
 *              </p>
 */
@MCElement(name="staticUserDataProvider")
public class StaticUserDataProvider implements UserDataProvider {

	private List<User> users = new ArrayList<>();
	private Map<String, User> usersByName = new HashMap<>();
	private SecureRandom random = new SecureRandom();
	private int saltByteSize = 128;
	private final PasswordEncoder passwordEncoder;

	public StaticUserDataProvider() {
		this.passwordEncoder = new BCryptPasswordEncoder();
	}

	@Override
	public Map<String, String> verify(Map<String, String> postData) {
		String username = postData.get("username");
		if (username == null)
			throw new NoSuchElementException("Username not provided.");
		if (username.equals("error"))
			throw new RuntimeException("Simulated error for username 'error'."); // For testing or specific error handling

		User user = usersByName.get(username);
		if (user == null)
			throw new NoSuchElementException("User '" + username + "' not found.");

		String providedPassword = postData.get("password");
		if (providedPassword == null) {
			throw new IllegalArgumentException("Password not provided.");
		}

		String storedPassword = user.getPassword();
		if (storedPassword == null) {
			throw new NoSuchElementException("No password configured for user '" + username + "'.");
		}

		// Try BCrypt first
		if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
			if (!passwordEncoder.matches(providedPassword, storedPassword)) {
				throw new NoSuchElementException("Incorrect password for user '" + username + "'.");
			}
		}
		// Then try Apache Crypt
		else if (isApacheCryptHashedPassword(storedPassword)) {
			String providedPasswordHash;
			try {
				// According to commons-codec javadoc, the full stored hash can be used as salt for verification.
				providedPasswordHash = Crypt.crypt(providedPassword, storedPassword);
			} catch (Exception e) {
				// An IllegalArgumentException might be thrown if the salt format (even from storedPassword) is not what Crypt expects
				throw new RuntimeException("Error verifying password with Apache Crypt for user '" + username + "': " + e.getMessage(), e);
			}

			if (!storedPassword.equals(providedPasswordHash)) {
				throw new NoSuchElementException("Incorrect password for user '" + username + "'.");
			}
		}
		// Fallback to plain text (for backward compatibility)
		else if (!storedPassword.equals(providedPassword)) {
			throw new NoSuchElementException("Incorrect password for user '" + username + "'.");
		}
		// If none of the above, and we haven't thrown, it's a match (e.g. plain text matched)

		return user.getAttributes();
	}

	/**
	 * Checks if the password string matches common Apache crypt formats ($1$, $apr1$, $5$, $6$).
	 * Does not attempt to validate the hash itself, only the prefix.
	 * @param password the stored password string
	 * @return true if it looks like an Apache crypt hash, false otherwise.
	 */
	public boolean isApacheCryptHashedPassword(String password) {
		if (password == null || !password.startsWith("$")) {
			return false;
		}
		// Exclude bcrypt patterns explicitly
		if (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$")) {
			return false;
		}
		// Check for typical Apache crypt prefixes
		return password.startsWith("$1$") || password.startsWith("$apr1$") || password.startsWith("$5$") || password.startsWith("$6$");
	}

	private String createPasswdCompatibleHash(String algo, String password, String salt) {
		return Crypt.crypt(password, "$" + algo + "$" + salt);
	}

	@MCElement(name="user", topLevel=false, id="staticUserDataProvider-user")
	public static class User {
		final Map<String, String> attributes = new HashMap<>();

		public User() {}

		public User(String username, String password){
			setUsername(username);
			setPassword(password);
		}

		public String getUsername() {
			return attributes.get("username");
		}

		/**
		 * @description The user's login.
		 */
		@MCAttribute
		public void setUsername(String value) {
			attributes.put("username", value);
		}

		public String getPassword() {
			return attributes.get("password");
		}

		/**
		 * @description The user's password.
		 */
		@MCAttribute
		public void setPassword(String value) {
			attributes.put("password", value);
		}

		public String getSms() {
			return attributes.get("sms");
		}

		/**
		 * @description The user's phone number (if used in combination with the {@link TelekomSMSTokenProvider}).
		 */
		@MCAttribute
		public void setSms(String value) {
			attributes.put("sms", value);
		}

		public String getSecret() {
			return attributes.get("secret");
		}

		/**
		 * @description The user's shared TOTP secret (if used in combination with the {@link TOTPTokenProvider}).
		 */
		@MCAttribute
		public void setSecret(String value) {
			attributes.put("secret", value);
		}

		public Map<String, String> getAttributes() {
			return attributes;
		}

		/**
		 * @description Other user attributes. For example any attribute starting with "<i>header</i>" will be added
		 *              when HTTP requests are forwarded when authorized by this user.
		 */
		@MCOtherAttributes
		public void setAttributes(Map<String, String> attributes) {
			this.attributes.putAll(attributes);
		}
	}

	public List<User> getUsers() {
		return users;
	}

	@MCChildElement
	public void setUsers(List<User> users) {
		getUsersByName().clear();
		for(User user : users){
			getUsersByName().put(user.getUsername(), user);
		}
		this.users = users;
	}

	public Map<String, User> getUsersByName() {
		return usersByName;
	}

	public void setUsersByName(Map<String, User> usersByName) {
		this.usersByName = usersByName;
	}

	@Override
	public void init(Router router) {
		for (User user : users)
			getUsersByName().put(user.getUsername(), user);
	}
}

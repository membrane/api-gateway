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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.net.SocketFactory;

import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;

/**
 * @description A <i>user data provider</i> querying an LDAP server to authorize users and retrieve attributes.
 * @explanation <p>
 *              The LDAP User Data Provider performs two jobs:
 *              <ol>
 *              <li>Authentication of a <i>username</i> and <i>password</i>.</li>
 *              <li>Retrieval of user attributes.</li>
 *              </ol>
 *              </p>
 *              <p>
 *              To achieve this, it first binds to <i>base</i> on the LDAP server <i>url</i>. If <i>binddn</i> is not
 *              present, it binds to the LDAP server anonymously, elsewise <i>binddn</i> and <i>bindpw</i> are used for
 *              authentication.
 *              </p>
 *              <p>
 *              Next, a search <i>searchPattern</i> with scope <i>searchScope</i> is executed where "<tt>%LOGIN%</tt>"
 *              is replaced by the escaped version of the <i>username</i>.
 *              </p>
 *              <p>
 *              The search returning no node or more than one node is treated as failure.
 *              </p>
 *              <p>
 *              If <i>passwordAttribute</i> is set, and the node has an attribute with this name and this attribute's
 *              value starts with "<tt>{x-plain}</tt>", the password is checked against the rest of the value for
 *              equality. If <i>passwordAttribute</i> is not set, a second binding is attempted on the node using the
 *              <i>password</i> the user provided.
 *              </p>
 *              <p>
 *              The user attribute keys specified in the mapping are then renamed according to the mapping and used for
 *              further processing (see the other modules of the <i>login</i> interceptor).
 *              </p>
 *              <p>
 *              </p>
 *              <p>
 *              For the initial binding, <i>connectTimeout</i> can be used to specify a timeout in milliseconds. For the
 *              search, <i>timeout</i> can be used.
 *              </p>
 *              <p>
 *              If <i>readAttributesAsSelf</i> is not set, the user attributes are collected from the search result. If
 *              it is set, an additional request is made after the second successful binding to retrieve the node's
 *              attributes.
 *              </p>
 */
@MCElement(name="ldapUserDataProvider", topLevel=false)
public class LDAPUserDataProvider implements UserDataProvider {

	private static Logger log = LoggerFactory.getLogger(LDAPUserDataProvider.class.getName());

	String url; // the LDAP server
	String base; // the base DN
	String binddn; // the DN to bind to the server, or null to bind anonymously
	String bindpw; // binddn's password, if binddn != null
	String searchPattern; // search expression to find user
	int searchScope = SearchControls.SUBTREE_SCOPE; // search scope to find user
	String passwordAttribute; // attribute containing the user's password, bind-to-authenticate to the user's node if null
	String timeout = "1000"; // timeout in milliseconds
	String connectTimeout = "1000";
	boolean readAttributesAsSelf = true; // whether reading the user's attributes requires authentication
	HashMap<String, String> attributeMap = new HashMap<String, String>(); // maps LDAP attributes to TokenGenerator attributes
	AttributeMap map;
	SSLParser sslParser;

	@MCElement(name="map", topLevel=false, id="ldapUserDataProvider-map")
	public static class AttributeMap {

		@MCElement(name="attribute", topLevel=false)
		public static class Attribute {
			String from;
			String to;

			public String getFrom() {
				return from;
			}

			@Required
			@MCAttribute
			public void setFrom(String from) {
				this.from = from;
			}

			public String getTo() {
				return to;
			}

			@Required
			@MCAttribute
			public void setTo(String to) {
				this.to = to;
			}
		}

		private List<Attribute> attributes = new ArrayList<Attribute>();

		public List<Attribute> getAttributes() {
			return attributes;
		}

		@MCChildElement
		public void setAttributes(List<Attribute> attributes) {
			this.attributes = attributes;
		}

	}

	/**
	 * @throws NoSuchElementException if no user could be found with the given login
	 * @throws AuthenticationException if the password does not match
	 * @throws CommunicationException e.g. on server timeout
	 * @throws NamingException on any other LDAP error
	 */
	private HashMap<String, String> auth(String login, String password) throws NamingException {
		Hashtable<String, String> env = new Hashtable<String, String>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, url);
		env.put("com.sun.jndi.ldap.read.timeout", timeout);
		env.put("com.sun.jndi.ldap.connect.timeout", connectTimeout);
		if (binddn != null) {
			env.put(Context.SECURITY_AUTHENTICATION, "simple");
			env.put(Context.SECURITY_PRINCIPAL, binddn);
			env.put(Context.SECURITY_CREDENTIALS, bindpw);
		}
		if (sslParser != null)
			env.put("java.naming.ldap.factory.socket", CustomSocketFactory.class.getName());

		HashMap<String, String> userAttrs = new HashMap<String, String>();
		String uid;

		DirContext ctx;
		ClassLoader old = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(CustomSocketFactory.class.getClassLoader());
			ctx = new InitialDirContext(env);
		} finally {
			Thread.currentThread().setContextClassLoader(old);
		}
		try {
			uid = searchUser(login, userAttrs, ctx);
		} finally {
			ctx.close();
		}

		if (passwordAttribute != null) {
			if (!userAttrs.containsKey("_pass"))
				throw new NoSuchElementException();
			String pass = userAttrs.get("_pass");
			if (pass == null || !pass.startsWith("{x-plain}"))
				throw new NoSuchElementException();
			log.debug("found password");
			pass = pass.substring(9);
			if (!pass.equals(password))
				throw new NoSuchElementException();
			userAttrs.remove("_pass");
		} else {
			env.put(Context.SECURITY_AUTHENTICATION, "simple");
			env.put(Context.SECURITY_PRINCIPAL, uid + "," + base);
			env.put(Context.SECURITY_CREDENTIALS, password);
			DirContext ctx2;
            ClassLoader old2 = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(CustomSocketFactory.class.getClassLoader());
                ctx2 = new InitialDirContext(env);
            } finally {
                Thread.currentThread().setContextClassLoader(old2);
            }
            try {
				if (readAttributesAsSelf)
					searchUser(login, userAttrs, ctx2);
			} finally {
				ctx2.close();
			}
		}
		return userAttrs;
	}

	private String searchUser(String login, HashMap<String, String> userAttrs,
			DirContext ctx) throws NamingException {
		String uid;
		SearchControls ctls = new SearchControls();
		ctls.setReturningObjFlag(true);
		ctls.setSearchScope(searchScope);
		String search = searchPattern.replaceAll(Pattern.quote("%LOGIN%"), escapeLDAPSearchFilter(login));
		log.debug("Searching LDAP for " + search);
		NamingEnumeration<SearchResult> answer = ctx.search(base, search, ctls);
		try {
			if (!answer.hasMore())
				throw new NoSuchElementException();
			log.debug("LDAP returned >=1 record.");
			SearchResult result = answer.next();
			uid = result.getName();
			for (Map.Entry<String, String> e : attributeMap.entrySet()) {
				log.debug("found LDAP attribute: " + e.getKey());
				Attribute a = result.getAttributes().get(e.getKey());
				if (a != null)
					userAttrs.put(e.getValue(), a.get().toString());
			}
		} finally {
			answer.close();
		}
		return uid;
	}

	private static final String escapeLDAPSearchFilter(String filter) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < filter.length(); i++) {
			char curChar = filter.charAt(i);
			switch (curChar) {
			case '\\':
				sb.append("\\5c");
				break;
			case '*':
				sb.append("\\2a");
				break;
			case '(':
				sb.append("\\28");
				break;
			case ')':
				sb.append("\\29");
				break;
			case '\u0000':
				sb.append("\\00");
				break;
			default:
				sb.append(curChar);
			}
		}
		return sb.toString();
	}

	@Override
	public Map<String, String> verify(Map<String, String> postData) {
		String username = postData.get("username");
		String password = postData.get("password");
		if (username == null || password == null)
			throw new NoSuchElementException();
		try {
			return auth(username, password);
		} catch (NoSuchElementException e) {
			throw e;
		} catch (AuthenticationException e) {
			log.debug("",e);
			throw new NoSuchElementException();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getUrl() {
		return url;
	}

	@Required
	@MCAttribute
	public void setUrl(String url) {
		this.url = url;
	}

	public String getBase() {
		return base;
	}

	@Required
	@MCAttribute
	public void setBase(String base) {
		this.base = base;
	}

	public String getBinddn() {
		return binddn;
	}

	@MCAttribute
	public void setBinddn(String binddn) {
		this.binddn = binddn;
	}

	public String getBindpw() {
		return bindpw;
	}

	@MCAttribute
	public void setBindpw(String bindpw) {
		this.bindpw = bindpw;
	}

	public String getSearchPattern() {
		return searchPattern;
	}

	@Required
	@MCAttribute
	public void setSearchPattern(String searchPattern) {
		this.searchPattern = searchPattern;
	}

	public static enum SearchScope {
		OBJECT,
		ONELEVEL,
		SUBTREE,
	}

	public SearchScope getSearchScope() {
		return SearchScope.values()[searchScope];
	}

	/**
	 * @default subtree
	 */
	@MCAttribute
	public void setSearchScope(SearchScope searchScope) {
		this.searchScope = searchScope.ordinal();
	}

	public String getPasswordAttribute() {
		return passwordAttribute;
	}

	@MCAttribute
	public void setPasswordAttribute(String passwordAttribute) {
		this.passwordAttribute = passwordAttribute;
		if (passwordAttribute != null) {
			attributeMap.put(passwordAttribute, "_pass");
		}
	}

	public String getTimeout() {
		return timeout;
	}

	/**
	 * @default 1000
	 */
	@MCAttribute
	public void setTimeout(String timeout) {
		this.timeout = timeout;
	}

	public String getConnectTimeout() {
		return connectTimeout;
	}

	/**
	 * @default 1000
	 */
	@MCAttribute
	public void setConnectTimeout(String connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public boolean isReadAttributesAsSelf() {
		return readAttributesAsSelf;
	}

	/**
	 * @default true
	 */
	@MCAttribute
	public void setReadAttributesAsSelf(boolean readAttributesAsSelf) {
		this.readAttributesAsSelf = readAttributesAsSelf;
	}

	public HashMap<String, String> getAttributeMap() {
		return attributeMap;
	}

	public void setAttributeMap(HashMap<String, String> attributeMap) {
		this.attributeMap = attributeMap;
		if (passwordAttribute != null) {
			attributeMap.put(passwordAttribute, "_pass");
		}
	}

	public SSLParser getSslParser() {
		return sslParser;
	}

	@MCChildElement(order=100, allowForeign = true)
	public void setSslParser(SSLParser sslParser) {
		this.sslParser = sslParser;
	}

	@Override
	public void init(Router router) {
		if (passwordAttribute != null && readAttributesAsSelf)
			throw new RuntimeException("@passwordAttribute is not compatible with @readAttributesAsSelf.");

		if (map != null) {
			for (AttributeMap.Attribute a : map.getAttributes())
				attributeMap.put(a.getFrom(), a.getTo());
		}
		if (passwordAttribute != null) {
			attributeMap.put(passwordAttribute, "_pass");
		}

		if (sslParser != null)
			CustomSocketFactory.sslContext = new StaticSSLContext(sslParser, router.getResolverMap(), router.getBaseLocation());
	}

	public AttributeMap getMap() {
		return map;
	}

	@MCChildElement(order=200)
	public void setMap(AttributeMap map) {
		this.map = map;
	}

	public static class CustomSocketFactory extends SocketFactory {
		public static SSLContext sslContext;
		public static int connectTimeout = 60000;

		private static CustomSocketFactory instance;

		public static CustomSocketFactory getDefault() {
			synchronized (CustomSocketFactory.class) {
				if (instance == null)
					instance = new CustomSocketFactory();
			}
			return instance;
		}

		public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
			return sslContext.createSocket(host, port, connectTimeout, host, null);
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
			return sslContext.createSocket(host, port, localHost, localPort, connectTimeout, host, null);
		}

		@Override
		public Socket createSocket(InetAddress host, int port) throws IOException {
			throw new RuntimeException("not implemented");
		}

		@Override
		public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
			throw new RuntimeException("not implemented");
		}

		public Socket createSocket() throws IOException {
			return sslContext.createSocket();
		}

	}
}

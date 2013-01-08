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

import java.util.HashMap;
import java.util.Hashtable;
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
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.AbstractXmlElement;

@MCElement(name="ldapUserDataProvider", group="userDataProvider", global=false, xsd=
		"<xsd:sequence>\r\n" + 
		"	<xsd:element name=\"map\">\r\n" + 
		"		<xsd:complexType>\r\n" + 
		"			<xsd:sequence>\r\n" + 
		"				<xsd:element name=\"attribute\" minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
		"					<xsd:complexType>\r\n" + 
		"						<xsd:sequence />\r\n" + 
		"						<xsd:attribute name=\"from\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"						<xsd:attribute name=\"to\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"					</xsd:complexType>\r\n" + 
		"				</xsd:element>\r\n" + 
		"			</xsd:sequence>\r\n" + 
		"		</xsd:complexType>\r\n" + 
		"	</xsd:element>\r\n" + 
		"</xsd:sequence>\r\n" + 
		"<xsd:attribute name=\"url\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"<xsd:attribute name=\"base\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"<xsd:attribute name=\"binddn\" type=\"xsd:string\" />\r\n" + 
		"<xsd:attribute name=\"bindpw\" type=\"xsd:string\" />\r\n" + 
		"<xsd:attribute name=\"searchPattern\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"<xsd:attribute name=\"searchScope\" type=\"xsd:string\" default=\"subtree\" />\r\n" + 
		"<xsd:attribute name=\"timeout\" type=\"xsd:string\" default=\"1000\" />\r\n" + 
		"<xsd:attribute name=\"connectTimeout\" type=\"xsd:string\" default=\"1000\" />\r\n" + 
		"<xsd:attribute name=\"readAttributesAsSelf\" type=\"xsd:boolean\" default=\"true\" />\r\n" + 
		"<xsd:attribute name=\"passwordAttribute\" type=\"xsd:string\" />\r\n")
public class LDAPUserDataProvider extends AbstractXmlElement implements UserDataProvider {

	private static Log log = LogFactory.getLog(LDAPUserDataProvider.class.getName());

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

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		url = token.getAttributeValue("", "url");
		base = token.getAttributeValue("", "base");
		binddn = token.getAttributeValue("", "binddn");
		bindpw = token.getAttributeValue("", "bindpw");
		searchPattern = token.getAttributeValue("", "searchPattern");
		searchScope = searchScopeFromString(token.getAttributeValue("", "searchScope"));
		passwordAttribute = token.getAttributeValue("", "passwordAttribute");
		timeout = StringUtils.defaultIfEmpty(token.getAttributeValue("", "timeout"), "1000");
		connectTimeout = StringUtils.defaultIfEmpty(token.getAttributeValue("", "connectTimeout"), timeout);
		readAttributesAsSelf = Boolean.parseBoolean(StringUtils.defaultIfEmpty(token.getAttributeValue("", "readAttributesAsSelf"), "true"));
		
		if (passwordAttribute != null && readAttributesAsSelf)
			throw new Exception("@passwordAttribute is not compatible with @readAttributesAsSelf.");
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (child.equals("map")) {
			new AbstractXmlElement() {
				protected void parseChildren(XMLStreamReader token, String child) throws Exception {
					if (child.equals("attribute")) {
						String from = token.getAttributeValue("", "from");
						String to = token.getAttributeValue("", "to");
						if (from == null || to == null)
							throw new Exception("ldapUserDataProvider/map/attribute requires @from and @to attributes.");
						attributeMap.put(from, to);
						new AbstractXmlElement() {}.parse(token);
					} else {
						super.parseChildren(token, child);
					}
				};
			}.parse(token);
		} else {
			super.parseChildren(token, child);
		}
		if (passwordAttribute != null) {
			attributeMap.put(passwordAttribute, "_pass");
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

		HashMap<String, String> userAttrs = new HashMap<String, String>();
		String uid;

		DirContext ctx = new InitialDirContext(env);
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
			DirContext ctx2 = new InitialDirContext(env);
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
			log.debug(e);
			throw new NoSuchElementException();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private int searchScopeFromString(String s) {
		if (s != null && "object".equalsIgnoreCase(s))
			return SearchControls.OBJECT_SCOPE;
		if (s != null && "onelevel".equalsIgnoreCase(s))
			return SearchControls.ONELEVEL_SCOPE;
		return SearchControls.SUBTREE_SCOPE;
	}

	/*
	private String searchScopeToString(int scope) {
		switch (scope) {
		case SearchControls.OBJECT_SCOPE: return "object";
		case SearchControls.ONELEVEL_SCOPE: return "onelevel";
		default: return "subtree";
		}
	}
	*/

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getBase() {
		return base;
	}

	public void setBase(String base) {
		this.base = base;
	}

	public String getBinddn() {
		return binddn;
	}

	public void setBinddn(String binddn) {
		this.binddn = binddn;
	}

	public String getBindpw() {
		return bindpw;
	}

	public void setBindpw(String bindpw) {
		this.bindpw = bindpw;
	}

	public String getSearchPattern() {
		return searchPattern;
	}

	public void setSearchPattern(String searchPattern) {
		this.searchPattern = searchPattern;
	}

	public int getSearchScope() {
		return searchScope;
	}

	public void setSearchScope(int searchScope) {
		this.searchScope = searchScope;
	}

	public String getPasswordAttribute() {
		return passwordAttribute;
	}

	public void setPasswordAttribute(String passwordAttribute) {
		this.passwordAttribute = passwordAttribute;
	}

	public String getTimeout() {
		return timeout;
	}

	public void setTimeout(String timeout) {
		this.timeout = timeout;
	}

	public String getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(String connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public boolean isReadAttributesAsSelf() {
		return readAttributesAsSelf;
	}

	public void setReadAttributesAsSelf(boolean readAttributesAsSelf) {
		this.readAttributesAsSelf = readAttributesAsSelf;
	}

	public HashMap<String, String> getAttributeMap() {
		return attributeMap;
	}

	public void setAttributeMap(HashMap<String, String> attributeMap) {
		this.attributeMap = attributeMap;
	}

}

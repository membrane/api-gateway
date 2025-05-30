/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.predic8.membrane.core.http.cookie;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A collection of cookies - reusable and tuned for server side performance.
 * Based on RFC2965 ( and 2109 )
 * <p>
 * This class is not synchronized.
 * <p>
 *
 * Source: <a href="http://tomcat.apache.org/">...</a>
 * License:  <a href="http://www.apache.org/licenses/LICENSE-2.0">...</a>
 *
 * @author Costin Manolache
 * @author kevin seguin
 */
public final class Cookies {

	private static final Logger log = LoggerFactory.getLogger(Cookies.class);

	// expected average number of cookies per request
	public static final int INITIAL_SIZE=4;
	ServerCookie[] scookies  = new ServerCookie[INITIAL_SIZE];
	int cookieCount = 0;
	boolean unprocessed = true;

	final MimeHeaders headers;

	/**
	 *  Construct a new cookie collection, that will extract
	 *  the information from headers.
	 *
	 * @param headers Cookies are lazy-evaluated and will extract the
	 *     information from the provided headers.
	 */
	public Cookies(MimeHeaders headers) {
		this.headers=headers;
	}

	/**
	 * Recycle.
	 */
	public void recycle() {
		for( int i=0; i< cookieCount; i++ ) {
			if( scookies[i]!=null ) {
				scookies[i].recycle();
			}
		}
		cookieCount=0;
		unprocessed=true;
	}

	/**
	 * EXPENSIVE!!!  only for debugging.
	 */
	@Override
	public String toString() {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		pw.println("=== Cookies ===");
		int count = getCookieCount();
		for (int i = 0; i < count; ++i) {
			pw.println(getCookie(i).toString());
		}
		return sw.toString();
	}

	// -------------------- Indexed access --------------------

	public ServerCookie getCookie( int idx ) {
		if( unprocessed ) {
			getCookieCount(); // will also update the cookies
		}
		return scookies[idx];
	}

	public int getCookieCount() {
		if( unprocessed ) {
			unprocessed=false;
			processCookies(headers);
		}
		return cookieCount;
	}

	// -------------------- Adding cookies --------------------

	/** Register a new, initialized cookie. Cookies are recycled, and
	 *  most of the time an existing ServerCookie object is returned.
	 *  The caller can set the name/value and attributes for the cookie
	 */
	private ServerCookie addCookie() {
		if( cookieCount >= scookies.length  ) {
			ServerCookie[] scookiesTmp =new ServerCookie[2*cookieCount];
			System.arraycopy( scookies, 0, scookiesTmp, 0, cookieCount);
			scookies=scookiesTmp;
		}

		ServerCookie c = scookies[cookieCount];
		if( c==null ) {
			c= new ServerCookie();
			scookies[cookieCount]=c;
		}
		cookieCount++;
		return c;
	}


	// code from CookieTools

	/** Add all Cookie found in the headers of a request.
	 */
	public void processCookies(MimeHeaders headers) {
		if (headers == null) {
			return; // nothing to process
		}
		headers.findHeaders("Cookie").stream()
				.map(MessageBytes::getByteChunk)
				.forEach(this::processCookieHeader);
	}

	// XXX will be refactored soon!
	private static boolean equals(String s, byte[] b, int start, int end) {
		int blen = end-start;
		if (b == null || blen != s.length()) {
			return false;
		}
		int boff = start;
		for (int i = 0; i < blen; i++) {
			if (b[boff++] != s.charAt(i)) {
				return false;
			}
		}
		return true;
	}


	/**
	 * Returns true if the byte is a whitespace character as
	 * defined in RFC2619
	 * JVK
	 */
	private static boolean isWhiteSpace(final byte c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
	}

	/**
	 * Unescapes any double quotes in the given cookie value.
	 *
	 * @param bc The cookie value to modify
	 */
	private static void unescapeDoubleQuotes(ByteChunk bc) {

		if (bc == null || bc.getLength() == 0 || bc.indexOf('"', 0) == -1) {
			return;
		}

		int src = bc.getStart();
		int end = bc.getEnd();
		int dest = src;
		byte[] buffer = bc.getBuffer();

		while (src < end) {
			if (buffer[src] == '\\' && buffer[src + 1] == '"') {
				src++;
			}
			buffer[dest] = buffer[src];
			dest ++;
			src ++;
		}
		bc.setEnd(dest);
	}

	/**
	 * Parses a cookie header after the initial "Cookie:"
	 * [WS][$]token[WS]=[WS](token|QV)[;|,]
	 * RFC 2965
	 * JVK
	 */
	private void processCookieHeader(ByteChunk bc) {
		byte[] bytes = bc.getBytes();
		int off = bc.getOffset();
		int len = bc.getLength();
		if( len<=0 || bytes==null ) {
			return;
		}
		int end=off+len;
		int pos=off;
		int nameStart=0;
		int nameEnd=0;
		int valueStart=0;
		int valueEnd=0;
		int version = 0;
		ServerCookie sc=null;
		boolean isSpecial;
		boolean isQuoted;

		while (pos < end) {
			isSpecial = false;
			isQuoted = false;

			pos += bc.followingBytesMatching(pos, Cookies::isWhitespaceOrNonTokenCharacter);

			if (pos >= end) {
				return;
			}

			// Detect Special cookies
			if (bytes[pos] == '$') {
				isSpecial = true;
				pos++;
			}

			// Get the cookie/attribute name. This must be a token
			valueEnd = valueStart = nameStart = pos;
			pos = nameEnd = getTokenEndPosition(bytes,pos,end,version,true);

			// Skip whitespace
			pos += bc.followingBytesMatching(pos, Cookies::isWhiteSpace);


			// Check for an '=' -- This could also be a name-only
			// cookie at the end of the cookie header, so if we
			// are past the end of the header, but we have a name
			// skip to the name-only part.
			if (pos < (end - 1) && bytes[pos] == '=') {

				// Skip '='
				pos++;

				// Skip whitespace
				pos += bc.followingBytesMatching(pos, Cookies::isWhitespaceOrNonTokenCharacter);

				if (pos >= end) {
					return;
				}

				// Determine what type of value this is, quoted value,
				// token, name-only with an '=', or other (bad)
				switch (bytes[pos]) {
				case '"': // Quoted Value
					isQuoted = true;
					valueStart=pos + 1; // strip "
					// getQuotedValue returns the position before
					// at the last quote. This must be dealt with
					// when the bytes are copied into the cookie
					valueEnd=getQuotedValueEndPosition(bytes,
							valueStart, end);
					// We need pos to advance
					pos = valueEnd;
					// Handles cases where the quoted value is
					// unterminated and at the end of the header,
					// e.g. [myname="value]
					if (pos >= end) {
						return;
					}
					break;
				case ';':
				case ',':
					// Name-only cookie with an '=' after the name token
					// This may not be RFC compliant
					valueStart = valueEnd = -1;
					// The position is OK (On a delimiter)
					break;
				default:
					if (version == 0 &&
					!CookieSupport.isV0Separator((char)bytes[pos]) &&
					CookieSupport.ALLOW_HTTP_SEPARATORS_IN_V0 ||
					!CookieSupport.isHttpSeparator((char)bytes[pos]) ||
					bytes[pos] == '=' && CookieSupport.ALLOW_EQUALS_IN_VALUE) {
						// Token
						valueStart=pos;
						// getToken returns the position at the delimiter
						// or other non-token character
						valueEnd=getTokenEndPosition(bytes, valueStart, end,
								version, false);
						// We need pos to advance
						pos = valueEnd;
					} else  {
						// INVALID COOKIE, advance to next delimiter
						// The starting character of the cookie value was
						// not valid.
						String message =
								"cookies.invalidCookieToken";
						log.debug(message);

						while (pos < end && bytes[pos] != ';' &&
								bytes[pos] != ',')
						{pos++; }
						pos++;
						// Make sure no special avpairs can be attributed to
						// the previous cookie by setting the current cookie
						// to null
						sc = null;
						continue;
					}
				}
			} else {
				// Name only cookie
				valueStart = valueEnd = -1;
				pos = nameEnd;

			}

			// We should have an avpair or name-only cookie at this
			// point. Perform some basic checks to make sure we are
			// in a good state.

			// Skip whitespace
			pos += bc.followingBytesMatching(pos, Cookies::isWhiteSpace);


			// Make sure that after the cookie we have a separator. This
			// is only important if this is not the last cookie pair
			pos += bc.followingBytesMatching(pos, b -> (b != ';' && b != ','));

			pos++;

			// All checks passed. Add the cookie, start with the
			// special avpairs first
			if (isSpecial) {
				isSpecial = false;
				// $Version must be the first avpair in the cookie header
				// (sc must be null)
				if (equals( "Version", bytes, nameStart, nameEnd) &&
						sc == null) {
					// Set version
					if( bytes[valueStart] =='1' && valueEnd == (valueStart+1)) {
						version=1;
					} else {
						// unknown version (Versioning is not very strict)
					}
					continue;
				}

				// We need an active cookie for Path/Port/etc.
				if (sc == null) {
					continue;
				}

				// Domain is more common, so it goes first
				if (equals( "Domain", bytes, nameStart, nameEnd)) {
					sc.getDomain().setBytes( bytes,
							valueStart,
							valueEnd-valueStart);
					continue;
				}

				if (equals( "Path", bytes, nameStart, nameEnd)) {
					sc.getPath().setBytes( bytes,
							valueStart,
							valueEnd-valueStart);
					continue;
				}

				// v2 cookie attributes - skip them
				if (equals( "Port", bytes, nameStart, nameEnd)) {
					continue;
				}
				if (equals( "CommentURL", bytes, nameStart, nameEnd)) {
					continue;
				}

				// Unknown cookie, complain
				String message = "cookies.invalidSpecial";
				log.debug(message);
			} else { // Normal Cookie
				if (valueStart == -1 && !CookieSupport.ALLOW_NAME_ONLY) {
					// Skip name only cookies if not supported
					continue;
				}

				sc = addCookie();
				sc.setVersion( version );
				sc.getName().setBytes( bytes, nameStart,
						nameEnd-nameStart);

				if (valueStart != -1) { // Normal AVPair
					sc.getValue().setBytes( bytes, valueStart,
							valueEnd-valueStart);
					if (isQuoted) {
						// We know this is a byte value so this is safe
						unescapeDoubleQuotes(sc.getValue().getByteChunk());
					}
				} else {
					// Name Only
					sc.getValue().setEmpty();
				}
            }
		}
	}

	private static boolean isWhitespaceOrNonTokenCharacter(byte bytes) {
		return CookieSupport.isHttpSeparator((char) bytes) &&
				!CookieSupport.ALLOW_HTTP_SEPARATORS_IN_V0 ||
				CookieSupport.isV0Separator((char) bytes) ||
				isWhiteSpace(bytes);
	}

	/**
	 * Given the starting position of a token, this gets the end of the
	 * token, with no separator characters in between.
	 * JVK
	 */
	private static int getTokenEndPosition(byte[] bytes, int off, int end,
                                           int version, boolean isName){
		int pos = off;
		while (pos < end &&
				(!CookieSupport.isHttpSeparator((char)bytes[pos]) ||
						version == 0 &&
						CookieSupport.ALLOW_HTTP_SEPARATORS_IN_V0 &&
						bytes[pos] != '=' &&
						!CookieSupport.isV0Separator((char)bytes[pos]) ||
						!isName && bytes[pos] == '=' &&
						CookieSupport.ALLOW_EQUALS_IN_VALUE)) {
			pos++;
		}

		return Math.min(pos, end);
	}

	/**
	 * Given a starting position after an initial quote character, this gets
	 * the position of the end quote. This escapes anything after a '\' char
	 * JVK RFC 2616
	 */
	private static int getQuotedValueEndPosition(byte[] bytes, int off, int end){
		int pos = off;
		while (pos < end) {
			if (bytes[pos] == '"') {
				return pos;
			} else if (bytes[pos] == '\\' && pos < (end - 1)) {
				pos+=2;
			} else {
				pos++;
			}
		}
		// Error, we have reached the end of the header w/o a end quote
		return end;
	}

}

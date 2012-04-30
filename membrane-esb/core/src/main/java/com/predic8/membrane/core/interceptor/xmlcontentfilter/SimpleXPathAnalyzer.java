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
package com.predic8.membrane.core.interceptor.xmlcontentfilter;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParser.ContainerNode;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParser.Node;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParser.RoundBracketNode;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParser.SquareBracketNode;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParser.StringNode;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParser.UnparsedStringNode;

/**
 * Utility methods for {@link XMLContentFilter}.
 */
public class SimpleXPathAnalyzer {
	
	/**
	 * Analyzes whether an XPath 2.0 string is a simple "UnionExpr" and splits
	 * it into its IntersectExceptExpr parts.
	 * 
	 * @return null, if the expression is not a UnionExpr; otherwise a list of
	 *         IntersectExceptExprs, which the UnionExpr consists of.
	 */
	public List<ContainerNode> getIntersectExceptExprs(String xpath) {
		ContainerNode node = new SimpleXPathParser().parse(xpath);
		
		if (!isUnionExpr(node))
			return null;

		return splitUnionExprIntoIntersectExceptExprs(node);
	}
	
	/**
	 * Checks whether a given expression requires the existence of a named element.
	 * @return The named element (including its namespace) or null if there is no such element.
	 */
	public QName getElement(ContainerNode intersectExceptExpr) {
		for (String op : new String[] { "intersect", "except", "instance", "treat", "castable", "cast", "+", "-" })
			for (Node n : intersectExceptExpr.nodes)
				if (n instanceof UnparsedStringNode)
					if (indexOfOperand(((UnparsedStringNode)n).s, op) != -1)
						return null;
		// check whether intersectExceptExpr starts with '//' + name
		if (intersectExceptExpr.nodes.length == 0)
			return null;
		if (!(intersectExceptExpr.nodes[0] instanceof UnparsedStringNode))
			return null;
		Marker m = new Marker(((UnparsedStringNode)intersectExceptExpr.nodes[0]).s);
		skipWhitespace(m);
		if (eatChar(m) != '/')
			return null;
		if (eatChar(m) != '/')
			return null;
		skipWhitespace(m);
		String elementName = getName(m);
		if (elementName == null)
			return null;
		skipWhitespace(m);
		// if '[' + expr + ']' follows
		if (m.isAtEnd() &&
			intersectExceptExpr.nodes.length > 1 &&
			intersectExceptExpr.nodes[1] instanceof SquareBracketNode) {
			ContainerNode predicate = ((SquareBracketNode)intersectExceptExpr.nodes[1]).node;
			if (predicate.nodes.length == 4 &&
				predicate.nodes[0] instanceof UnparsedStringNode &&
				predicate.nodes[1] instanceof RoundBracketNode &&
				((RoundBracketNode)predicate.nodes[1]).node.nodes.length == 0 &&
				predicate.nodes[2] instanceof UnparsedStringNode &&
				predicate.nodes[3] instanceof StringNode) {
				Marker m2 = new Marker(((UnparsedStringNode)predicate.nodes[0]).s);	
				skipWhitespace(m2);
				if ("namespace-uri".equals(getName(m2)) && m2.isAtEnd()) {
					return new QName(((StringNode)predicate.nodes[3]).s, elementName);
				}
			}
		}
		return new QName(elementName);
	}
	
	private String getName(Marker m) {
		StringBuffer sb = new StringBuffer();
		while(true) {
			int c = eatChar(m);
			if (isNameChar(c))
				sb.appendCodePoint(c);
			else
				break;
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	private int eatChar(Marker m) {
		return m.p == m.s.length() ? 0 : m.s.codePointAt(m.p++);
	}

	private void skipWhitespace(Marker m) {
		while (m.p != m.s.length() && isWhiteSpace(m.s.codePointAt(m.p)))
			m.p++;
	}

	/**
	 * Remembers where we are while parsing.
	 */
	private static class Marker {
		/** The string we are parsing */
		public final String s;
		/** The position we are at */
		public int p;
		
		public Marker(String s) {
			this.s = s;
			p = 0;
		}

		public boolean isAtEnd() {
			return p == s.length();
		}
	}
	
	private boolean isUnionExpr(ContainerNode node) {
		// an expression is a UnionExpr, if it does not contain any operators with lower precedence
		for (String op : new String[] { ",", "return", "for", "some", "every", "if", "or", "and", 
				"eq", "ne", "lt", "le", "gt", "ge", "=", "!=", "<", "<=", ">", ">=", "is", "<<", ">>",
				"to", "+", "-", "*", "div", "idiv", "mod"})
			for (Node n : node.nodes)
				if (n instanceof UnparsedStringNode)
					if (indexOfOperand(((UnparsedStringNode)n).s, op) != -1)
						return false;
		return true;
	}
	
	private List<ContainerNode> splitUnionExprIntoIntersectExceptExprs(ContainerNode node) {
		List<ContainerNode> res = new ArrayList<ContainerNode>();
		List<Node> intersectExceptExprParts = new ArrayList<Node>();
		for (Node n : node.nodes)
			if (n instanceof UnparsedStringNode) {
				List<String> parts = new ArrayList<String>();
				for (String part : splitOnOperand(((UnparsedStringNode)n).s, "|"))
					for (String part2 : splitOnOperand(part, "union"))
						parts.add(part2);
				for (int i = 0; i < parts.size(); i++) {
					if (i >= 1) {
						// next IntersectExceptExpr
						res.add(new ContainerNode(intersectExceptExprParts.toArray(new Node[0])));
						intersectExceptExprParts = new ArrayList<Node>();
					}
					intersectExceptExprParts.add(new UnparsedStringNode(parts.get(i)));
				}
			} else {
				intersectExceptExprParts.add(n);
			}
		if (intersectExceptExprParts.size() > 0)
			res.add(new ContainerNode(intersectExceptExprParts.toArray(new Node[0])));
		return res;
	}

	private List<String> splitOnOperand(String xpath, String op) {
		int p = indexOfOperand(xpath, op);
		if (p == -1) {
			List<String> res = new ArrayList<String>();
			res.add(xpath);
			return res;
		}
		List<String> res = splitOnOperand(xpath.substring(p + op.length()), op);
		res.add(0, xpath.substring(0, p));
		return res;
	}
	
	/** Ensure that any occurrence of op is not part of a name, if op itself is a name */
	private int indexOfOperand(String xpath, String op) {
		// 'letter' here refers to a character that can be part of a name
		int p = -1;
		while (true) {
			p = xpath.indexOf(op, p+1);
			if (p == -1)
				return -1;
			// if op starts with a 'letter' and the previous character exists and is a 'letter'
			if (isNameChar(op.codePointAt(0)) && 
					p > 0 && 
					isNameChar(xpath.codePointAt(p-1)))
				continue;
			// if op ends with a 'letter' and the following character exists and is a 'letter'
			if (isNameChar(op.codePointAt(op.length()-1)) && 
					p + op.length() < xpath.length() && 
					isNameChar(xpath.charAt(p + op.length())))
				continue;
			return p;
		}
	}

	
	/** See http://www.w3.org/TR/REC-xml/#NT-NameChar . */
	private boolean isNameChar(int c) {
		return c == ':' || 
				(c >= 'A' && c <= 'Z') ||
				c == '_' ||
				(c >= 'a' && c <= 'z') ||
				(c >= '\u00C0' && c <= '\u00D6') ||
				(c >= '\u00D8' && c <= '\u00F6') ||
				(c >= '\u00F8' && c <= '\u02FF') ||
				(c >= '\u0370' && c <= '\u037D') ||
				(c >= '\u037F' && c <= '\u1FFF') ||
				(c >= '\u200C' && c <= '\u200D') ||
				(c >= '\u2070' && c <= '\u218F') ||
				(c >= '\u2C00' && c <= '\u2FEF') ||
				(c >= '\u3001' && c <= '\uD7FF') ||
				(c >= '\uF900' && c <= '\uFDCF') ||
				(c >= '\uFDF0' && c <= '\uFFFD') ||
				(c >= 0x10000 && c <= 0xEFFFF) ||
				c == '-' ||
				c == '.' ||
				(c >= '0' && c <= '9') ||
				c == '\u00B7' ||
				(c >= '\u0300' && c <= '\u036F') ||
				(c >= '\u203F' && c <= '\u2040');
	}
	
	private boolean isWhiteSpace(int c) {
		return c == 0x20 || c == 0x9 || c == 0xD || c == 0xA;
	}
}

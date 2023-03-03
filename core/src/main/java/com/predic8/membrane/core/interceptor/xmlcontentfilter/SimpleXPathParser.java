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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Partially parses XPath expressions. ("Partially" meaning that we are not
 * interested in the full XPath grammar: Uninteresting parts are left as
 * "unparsed string nodes" as long as we are able to parse the interesting
 * parts.)
 *
 * An XPath expression, given as a string, is transformed into a tree of nodes
 * (instances of subclasses of {@link Node}).
 *
 * We are only interested in strings, comments, round- and
 * square-bracket-expressions. Any other substring of the expression is
 * concatenated into a node of type {@link UnparsedStringNode}.
 *
 * See the XPath 2.0 spec at <a href="http://www.w3.org/TR/xpath20/">...</a> .
 */
public class SimpleXPathParser {
	/**
	 * Main entry point into this class.
	 */
	public ContainerNode parse(String xpath) {
		return parseNormal(new Marker(xpath), -1);
	}

	/**
	 * This class together with its subclasses are used to build our syntax tree.
	 */
	public static abstract class Node {
		/**
		 * Prints debugging information about the syntax tree to the stream.
		 */
		public abstract void print(PrintStream ps);
	}

	public static class UnparsedStringNode extends Node {
		protected String s;

		public UnparsedStringNode(String s) {
			this.s = s;
		}

		@Override
		public void print(PrintStream ps) {
			ps.print("UNPARSED(" + s + ")");
		}
	}

	public static class StringNode extends Node {
		protected String s;

		public StringNode(String s) {
			this.s = s;
		}

		@Override
		public void print(PrintStream ps) {
			ps.print("STRING(" + s + ")");
		}
	}

	public static class CommentNode extends Node {
		protected String s;

		public CommentNode(String s) {
			this.s = s;
		}

		@Override
		public void print(PrintStream ps) {
			ps.print("COMMENT(" + s + ")");
		}
	}

	public static class ContainerNode extends Node {
		protected Node[] nodes;

		public ContainerNode(Node... nodes) {
			this.nodes = nodes;
		}

		@Override
		public void print(PrintStream ps) {
			ps.print("CONTAINER(");
			for (Node n : nodes) {
				n.print(ps);
				ps.print(",");
			}
			ps.print(")");
		}
	}

	public static class SquareBracketNode extends Node {
		protected ContainerNode node;

		private SquareBracketNode(ContainerNode node) {
			this.node = node;
		}

		@Override
		public void print(PrintStream ps) {
			ps.print("SQUARE(");
			node.print(ps);
			ps.print(")");
		}
	}

	public static class RoundBracketNode extends Node {
		protected ContainerNode node;

		private RoundBracketNode(ContainerNode node) {
			this.node = node;
		}

		@Override
		public void print(PrintStream ps) {
			ps.print("ROUND(");
			node.print(ps);
			ps.print(")");
		}
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
	}

	/**
     * Parses an XPath Expression (production "XPath" of the EBNF grammar of
     * <a href="http://www.w3.org/TR/xpath20/#nt-bnf">...</a> ).
     *
     * @param m
     *            the XPath expression and the position of the first character
     * @param terminator
     *            if -1, the end of the string terminates the XPath expression
     *            to parse. If != -1, a Unicode code point of "terminator"
     *            terminates the XPath expression to parse.
     */
	private ContainerNode parseNormal(final Marker m, int terminator) {
		StringBuilder sb2 = new StringBuilder();
		List<Node> result = new ArrayList<Node>();
		while (true) {
			if (m.p == m.s.length()) {
				if (terminator != -1)
					throw new RuntimeException("Unbalanced XPath expression");
				break;
			}
			int c = m.s.codePointAt(m.p++);
			if (c != '\'' && c != '"' && c != '(' && c != '['
					&& c != terminator) {
				sb2.appendCodePoint(c);
				continue;
			}
			if (sb2.length() > 0) {
				result.add(new UnparsedStringNode(sb2.toString()));
				sb2 = new StringBuilder();
			}
			switch (c) {
			case '\'':
			case '"':
				result.add(parseString(m, c));
				break;
			case '(':
				if (m.p == m.s.length())
					throw new RuntimeException("unbalanced XPath expression.");
				if (m.s.codePointAt(m.p) == ':')
					result.add(parseComment(m));
				else
					result.add(new RoundBracketNode(parseNormal(m, ')')));
				break;
			case '[':
				result.add(new SquareBracketNode(parseNormal(m, ']')));
				break;
			}
			if (c == terminator) {
				break;
			}
		}
		if (sb2.length() > 0) {
			result.add(new UnparsedStringNode(sb2.toString()));
			sb2 = new StringBuilder();
		}
		return new ContainerNode(result.toArray(new Node[0]));
	}

	/**
     * Parses a comment (production "Comment" of the EBNF grammar of
     * <a href="http://www.w3.org/TR/xpath20/#terminal-symbols">...</a> ).
     *
     * @param m
     *            the XPath expression and the position of the first character
     */
	private Node parseComment(final Marker m) {
		int comment_start = m.p += 2;
		int depth = 1;
		while (m.p < m.s.length()) {
			int c = m.s.codePointAt(m.p++);
			switch (c) {
			case ':':
				if (m.p == m.s.length())
					throw new RuntimeException("Unbalanced comment.");
				if (m.s.codePointAt(m.p++) == ')')
					if (--depth == 0) {
						return new CommentNode(m.s.substring(comment_start,
								m.p - 1));
					}
				break;
			case '(':
				if (m.p == m.s.length())
					throw new RuntimeException("Unbalanced comment.");
				if (m.s.codePointAt(m.p++) == ':')
					depth++;
				break;
			}
		}
		throw new RuntimeException("Unbalanced comment.");
	}

	/**
     * Parses a String (production "StringLiteral" of the EBNF grammar of
     * <a href="http://www.w3.org/TR/xpath20/#terminal-symbols">...</a> ).
     *
     * @param m
     *            the XPath expression and the position of the first character
     * @param terminator
     *            the character terminating the string ('"' or '\'')
     */
	private StringNode parseString(final Marker m, int terminator) {
		StringBuilder sb = new StringBuilder();
		while (true) {
			int q = m.s.indexOf(terminator, m.p);
			if (q == -1)
				throw new RuntimeException("Unbalanced string.");
			sb.append(m.s.substring(m.p, q));
			if (q == m.s.length() - 1 || m.s.codePointAt(q + 1) != terminator) {
				m.p = q + 1;
				break;
			}
			sb.appendCodePoint(terminator);
			m.p = q + 2;
		}
		return new StringNode(sb.toString());
	}

}

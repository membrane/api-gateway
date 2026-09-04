/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

/**
 * How far consuming an {@link AbstractBody} has got. A body starts {@link #UNREAD} and moves at most
 * once into one of the two terminal states, so "read" and "failed" can never both hold.
 * <p>
 * This is deliberately not the whole story of a body: whether it was <i>streamed</i> (passed through
 * without being retained) is an orthogonal property, see {@link AbstractBody#wasStreamed()}. A
 * streamed body is still {@link Read} once it has been consumed completely.
 */
sealed interface BodyState {

	/** Nothing consumed yet: the only state a body may be read, streamed or failed from. */
	record Unread() implements BodyState {}

	/** Consumed completely. For a buffered body the content is in the chunks; for a streamed one it is gone. */
	record Read() implements BodyState {}

	/**
	 * Reading failed. {@code cause} is re-thrown on every later access so the original failure stays
	 * visible instead of being replaced by a follow-up error such as "stream is closed".
	 */
	record Failed(ReadingBodyException cause) implements BodyState {}

	BodyState UNREAD = new Unread();
	BodyState READ = new Read();
}

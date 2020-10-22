/* Copyright 2020 predic8 GmbH, www.predic8.com

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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public abstract class BodyCollectingMessageObserver implements MessageObserver {

    private final Strategy strategy;
    private final long limit;
    private final List<Chunk> chunks = new ArrayList<>();
    private boolean truncated = false;
    private long storedSize = 0;

    public enum Strategy {
        /**
         * Throw an error when the body exceeds the limit.
         */
        ERROR,

        /**
         * Truncate the body when it exceeds the limit.
         */
        TRUNCATE
    }

    public BodyCollectingMessageObserver(Strategy strategy, long limit) {
        this.strategy = strategy;
        this.limit = limit;
    }

    @Override
    public void bodyChunk(byte[] buffer, int offset, int length) {
        byte[] shortContent = new byte[length];
        System.arraycopy(buffer, offset, shortContent, 0, length);
        Chunk chunk = new Chunk(shortContent);
        bodyChunk(chunk);
    }

    @Override
    public void bodyChunk(Chunk chunk) {
        if (limit != -1 && storedSize >= limit) {
            if (strategy == Strategy.ERROR)
                throw new RuntimeException("Body is too large for buffering.");
            truncated = true;
            return;
        }
        chunks.add(chunk);
        storedSize += chunk.getLength();
    }

    public AbstractBody getBody(AbstractBody body) throws IOException {
        if (!body.wasStreamed()) {
            return body;
        }
        return new Body(new BodyInputStream(chunks), storedSize);
    }

    public boolean isTruncated() {
        return truncated;
    }
}

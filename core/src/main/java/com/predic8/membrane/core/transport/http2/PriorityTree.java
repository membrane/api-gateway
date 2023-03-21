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

package com.predic8.membrane.core.transport.http2;

import com.google.common.collect.*;
import com.predic8.membrane.core.transport.http2.frame.*;
import org.slf4j.*;

import javax.validation.constraints.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.transport.http2.frame.Error.*;
import static org.apache.commons.lang3.StringUtils.*;

public class PriorityTree {

    private static final Logger log = LoggerFactory.getLogger(PriorityTree.class);

    private final StreamInfo root = new StreamInfo(0, null, null, null);

    public void reprioritize(@NotNull StreamInfo stream, int weight, @Null StreamInfo parent, boolean exclusive) throws IOException {
        if (parent == stream)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        if (parent == null)
            parent = root;

        if (log.isDebugEnabled())
            log.debug("reprioritize(streamId=" + stream.getStreamId() + ", weight=" + weight + ", parentStreamId=" + parent.getStreamId() + ", exclusive=" + exclusive + ")");

        if (isChildOf(parent, stream)) {
            parent.getPriorityParent().getPriorityChildren().remove(parent);
            parent.setPriorityParent(null);

            StreamInfo otherNewParent = stream.getPriorityParent();
            otherNewParent.getPriorityChildren().add(parent);
            parent.setPriorityParent(otherNewParent);
        }

        if (exclusive) {
            for (StreamInfo streamInfo : parent.getPriorityChildren())
                streamInfo.setPriorityParent(stream);
            stream.getPriorityChildren().addAll(parent.getPriorityChildren());
            parent.getPriorityChildren().clear();
        }

        if (stream.getPriorityParent() != null)
            stream.getPriorityParent().getPriorityChildren().remove(stream);

        parent.getPriorityChildren().add(stream);
        stream.setWeight(weight);
        stream.setPriorityParent(parent);

        if (log.isTraceEnabled())
            log.trace("\n" + toString());
    }

    private boolean isChildOf(StreamInfo a, StreamInfo b) {
        StreamInfo p = a;
        while (true) {
            p = p.getPriorityParent();
            if (p == null)
                return false;
            if (p == b)
                return true;
        }
    }

    private static List<StringBuilder> toStringBuilderList(StreamInfo node) {
        if (node.getPriorityChildren().size() == 0)
            return Lists.newArrayList(toStringBuilder(node));
        List<StreamInfo> priorityChildren = node.getPriorityChildren();
        List<List<StringBuilder>> cols = new ArrayList<>(priorityChildren.size());
        for (StreamInfo streamInfo : priorityChildren)
            cols.add(toStringBuilderList(streamInfo));
        List<StringBuilder> res = merge(cols);
        res.add(0, fill(toStringBuilder(node), res.get(0).length()));
        return res;
    }

    private static StringBuilder toStringBuilder(StreamInfo node) {
        return new StringBuilder(""+node.getStreamId() + " w" + node.getWeight() + " " + node.getState());
    }

    private static StringBuilder fill(StringBuilder sb, int length) {
        while (sb.length() < length)
            sb.append(' ');
        return sb;
    }

    private static List<StringBuilder> merge(List<List<StringBuilder>> matrix) {
        int row = 0;
        int orig = matrix.get(0).get(0).length();
        while (true) {
            StringBuilder rowSb = null;
            int spacesToInsert = 0;
            for (int col = 0; col < matrix.size(); col++) {
                if (rowSb == null && row < matrix.get(col).size()) {
                    rowSb = matrix.get(col).get(row);

                    rowSb.insert(0, repeat(' ', spacesToInsert));
                    rowSb.append("  ");
                    continue;
                }

                int spaces = (col == 0 ? orig : matrix.get(col).get(0).length()) + 2;

                if (rowSb != null) {
                    if (row < matrix.get(col).size()) {
                        rowSb.append(matrix.get(col).get(row));
                        rowSb.append("  ");
                    } else {
                        rowSb.append(repeat(' ', spaces));
                    }
                }

                spacesToInsert += spaces;
            }
            if (rowSb == null)
                break;

            if (row >= matrix.get(0).size())
                matrix.get(0).add(rowSb);

            row++;
        }
        return matrix.get(0);

    }

    @Override
    public String toString() {
        return String.join("\n", toStringBuilderList(root));
    }
}

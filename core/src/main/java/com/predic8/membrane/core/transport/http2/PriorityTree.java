package com.predic8.membrane.core.transport.http2;

import com.google.common.collect.Lists;
import com.predic8.membrane.core.transport.http2.frame.Error;
import com.predic8.membrane.core.transport.http2.frame.FatalConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.repeat;

public class PriorityTree {

    private static final Logger log = LoggerFactory.getLogger(PriorityTree.class);

    private StreamInfo root = new StreamInfo(0, null);

    public void reprioritize(@NotNull StreamInfo stream, int weight, @Null StreamInfo parent, boolean exclusive) throws IOException {
        if (parent == stream)
            throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR);

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

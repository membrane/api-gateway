package com.predic8.membrane.core.interceptor.adminApi;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AdminApiWebSocketServer implements WebSocketInterceptorInterface {

    @Override
    public void init(Router router) throws Exception {}

    @Override
    public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
        System.out.println("------------------------------------------------------------------");
        if (!frameTravelsToRight) return;

        String action = getAction(frame);
        if (action == null) return;
        switch (action) {
            case "sysInfo":
                handleSysInfo(frame);
                break;
            case "updateData":
                handleUpdateData(frame);
                break;
            default:
                handleUnknownAction(frame, action);
                break;
        }

    }

    private static @Nullable String getAction(WebSocketFrame frame) {
        String action;
        try {
            //TODO adjust format
            action = new JSONObject(new String(frame.getPayload(), UTF_8)).optString("action", "");
        } catch (Exception e) {
            JSONObject errorJson = new JSONObject();
            errorJson.put("error", "Error parsing: " + e.getMessage());
            setResponse(frame, errorJson);
            return null;
        }
        return action;
    }

    private void handleSysInfo(WebSocketFrame frame) {
        JSONObject systemInfoJson = new JSONObject();

        // Basic runtime details
        Runtime runtime = Runtime.getRuntime();
        systemInfoJson.put("maxMemory", runtime.maxMemory());
        systemInfoJson.put("totalMemory", runtime.totalMemory());
        systemInfoJson.put("freeMemory", runtime.freeMemory());
        systemInfoJson.put("availableProcessors", runtime.availableProcessors());
        systemInfoJson.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());

        // Thread details
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        systemInfoJson.put("threadCount", threadMXBean.getThreadCount());
        systemInfoJson.put("peakThreadCount", threadMXBean.getPeakThreadCount());
        systemInfoJson.put("totalStartedThreadCount", threadMXBean.getTotalStartedThreadCount());
        systemInfoJson.put("threads", getThreadsArray(threadMXBean));

        System.out.println("systemInfoJson = " + systemInfoJson);

        setResponse(frame, systemInfoJson);
    }

    private void handleUpdateData(WebSocketFrame frame) {
        //TODO
    }

    private void handleUnknownAction(WebSocketFrame frame, String action) {
        JSONObject errorJson = new JSONObject();
        errorJson.put("error", "Error processing action: " + action);
        setResponse(frame, errorJson);
    }

    private static void setResponse(WebSocketFrame frame, JSONObject jsonObject) {
        frame.setPayload(jsonObject.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static @NotNull JSONArray getThreadsArray(ThreadMXBean threadMXBean) {
        JSONArray threadsArray = new JSONArray();
        for (long threadId : threadMXBean.getAllThreadIds()) {
            ThreadInfo threadInfo = threadMXBean.getThreadInfo(threadId);
            if (threadInfo != null) {
                JSONObject threadJson = new JSONObject();
                threadJson.put("threadId", threadInfo.getThreadId());
                threadJson.put("threadName", threadInfo.getThreadName());
                threadJson.put("threadState", threadInfo.getThreadState().toString());
                threadsArray.put(threadJson);
            }
        }
        return threadsArray;
    }
}

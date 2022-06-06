package com.predic8.membrane.core.kubernetes.client;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.Map;

public interface Watcher {
    public void onEvent(@NotNull WatchAction action, @NotNull Map m);
    public void onClosed(@Nullable Throwable t);
}

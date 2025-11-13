package com.predic8.membrane.tutorials;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.ConsoleWatcher;
import com.predic8.membrane.examples.util.Process2;

import java.io.IOException;

public abstract class AbstractMembraneTutorialTest extends AbstractSampleMembraneStartStopTestcase {

    protected abstract String getTutorialDir();
    protected abstract String getTutorialYaml();

    @Override
    protected String getExampleDirName() {
        return "../tutorials/%s".formatted(getTutorialDir());
    }

    @Override
    protected Process2 startServiceProxyScript(ConsoleWatcher watch, String script) throws IOException, InterruptedException {
        Process2.Builder builder = new Process2.Builder().in(baseDir);
        if (watch != null)
            builder = builder.withWatcher(watch);
        return builder.script(script).waitForMembrane().parameters("-c %s".formatted(getTutorialYaml())).start();
    }

}
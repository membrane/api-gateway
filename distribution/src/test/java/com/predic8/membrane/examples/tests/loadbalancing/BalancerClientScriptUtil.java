/* Copyright 2012-2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests.loadbalancing;

import com.predic8.membrane.examples.util.*;

import java.io.*;

import static com.predic8.membrane.examples.util.Process2.isWindows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BalancerClientScriptUtil {

    public static void addNodeViaScript(File base, String nodeHost, int nodePort) throws IOException, InterruptedException {
        controlNodeViaScript(base, "up", nodeHost, nodePort);
    }

    public static  void removeNodeViaScript(File base, String nodeHost, int nodePort) throws IOException, InterruptedException {
        controlNodeViaScript(base, "down", nodeHost, nodePort);
    }

    public static  void controlNodeViaScript(File base, String command, String nodeHost, int nodePort) throws IOException, InterruptedException {
        controlNodeViaScript(0, base, command, nodeHost, nodePort);
    }

    public static  void controlNodeViaScript(int expectedReturnCode, File base, String command, String nodeHost, int nodePort) throws IOException, InterruptedException {
        Process2 lbclient = new Process2.Builder().in(base)
//				.withWatcher(new ConsoleLogger())
                .executable(getClientStartCommand(command, nodeHost, nodePort)).start();
        try {
            assertEquals(expectedReturnCode, lbclient.waitFor(30000));
        } finally {
            lbclient.killScript();
        }
    }

    public static  String getClientStartCommand(String command, String nodeHost, int nodePort) {
        if (isWindows())
            return  "cmd /c lbclient.bat " + command + " " + nodeHost + " " + nodePort;

        return "bash lbclient.sh " + command + " " + nodeHost + " " + nodePort;
    }
}

/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.cli.util;

import tools.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.cli.MembraneCommandLine;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;

import static java.lang.Integer.parseInt;
import static java.lang.System.exit;
import static java.nio.file.Files.writeString;
import static java.nio.file.Paths.get;
import static org.jose4j.jwk.JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE;
import static org.jose4j.jwk.JsonWebKey.OutputControlLevel.PUBLIC_ONLY;
import static org.jose4j.jwk.RsaJwkGenerator.generateJwk;

public class JwkGenerator {
    private static final Logger log = LoggerFactory.getLogger(JwkGenerator.class);

    public static void generateJWK(MembraneCommandLine commandLine) {
        int bits = 2048;
        String bitsArg = commandLine.getCommand().getOptionValue("b");
        if (bitsArg != null) {
            bits = parseInt(bitsArg);
        }

        boolean overwrite = commandLine.getCommand().isOptionSet("overwrite");
        String outputFile = commandLine.getCommand().getOptionValue("o");

        if (outputFile == null) {
            log.error("Missing required option: -o <output file>");
            commandLine.getCommand().printHelp();
            exit(1);
        }

        RsaJsonWebKey rsaJsonWebKey;
        try {
            rsaJsonWebKey = generateJwk(bits);
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
        rsaJsonWebKey.setKeyId(new BigInteger(130, new SecureRandom()).toString(32));
        rsaJsonWebKey.setUse("sig");
        rsaJsonWebKey.setAlgorithm("RS256");

        Path path = get(outputFile);
        if (path.toFile().exists() && !overwrite) {
            log.error("Output file ({}) already exists.", outputFile);
            exit(1);
        }
        try {
            writeString(path, rsaJsonWebKey.toJson(INCLUDE_PRIVATE));
        } catch (IOException e) {
            log.error(e.getMessage());
            exit(1);
        }
    }

    public static void privateJWKtoPublic(String input, String output) {
        try {
            Map map = new ObjectMapper().readValue(new File(input), Map.class);
            RsaJsonWebKey rsa = new RsaJsonWebKey(map);
            writeString(get(output), rsa.toJson(PUBLIC_ONLY));
        } catch (IOException | JoseException e) {
            log.error(e.getMessage());
            exit(1);
        }
    }

}

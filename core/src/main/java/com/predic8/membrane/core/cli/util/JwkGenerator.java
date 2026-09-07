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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.cli.InvalidOptionValueException;
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

import static java.lang.System.exit;
import static java.nio.file.Files.*;
import static java.nio.file.Paths.get;
import static org.jose4j.jwk.JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE;
import static org.jose4j.jwk.JsonWebKey.OutputControlLevel.PUBLIC_ONLY;
import static org.jose4j.jwk.RsaJwkGenerator.generateJwk;

public class JwkGenerator {
    private static final Logger log = LoggerFactory.getLogger(JwkGenerator.class);

    public static void generateJWK(MembraneCommandLine commandLine) {
        int bits = commandLine.getCommand().getIntOptionValue("b", 2048, 2048, 16384);

        // -o is a required option, so the value is always there.
        Path output = get(commandLine.getCommand().getTrimmedOptionValue("o"));
        checkOutput(output, commandLine.getCommand().isOptionSet("overwrite"));

        RsaJsonWebKey rsaJsonWebKey;
        try {
            rsaJsonWebKey = generateJwk(bits);
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
        rsaJsonWebKey.setKeyId(new BigInteger(130, new SecureRandom()).toString(32));
        rsaJsonWebKey.setUse("sig");
        rsaJsonWebKey.setAlgorithm("RS256");

        try {
            writeString(output, rsaJsonWebKey.toJson(INCLUDE_PRIVATE));
        } catch (IOException e) {
            log.error(e.getMessage());
            exit(1);
        }
    }

    public static void privateJWKtoPublic(String input, String output, boolean overwrite) {
        checkFiles(input, output, overwrite);
        try {
            Map map = new ObjectMapper().readValue(new File(input), Map.class);
            RsaJsonWebKey rsa = new RsaJsonWebKey(map);
            writeString(get(output), rsa.toJson(PUBLIC_ONLY));
        } catch (IOException | JoseException e) {
            log.error(e.getMessage());
            exit(1);
        }
    }

    /**
     * Guards the conversion against destroying key material: writing the public JWK onto the input
     * file silently discarded the private key components (see issue #3219).
     *
     * @throws InvalidOptionValueException if the input does not exist, or if the output is the input
     *                                     file or an existing file the user did not allow to replace
     */
    static void checkFiles(String input, String output, boolean overwrite) {
        Path in = get(input);
        Path out = get(output);
        if (!isRegularFile(in))
            throw new InvalidOptionValueException("Invalid value for -i: '%s' is not a file.".formatted(input));
        if (!exists(out))
            return;
        if (pointToSameFile(in, out))
            throw new InvalidOptionValueException("Invalid value for -o: '%s' is the input file. Converting it in place would destroy the private key.".formatted(output));
        checkOutput(out, overwrite);
    }

    /**
     * @throws InvalidOptionValueException if the output exists and the user did not allow to replace it
     */
    static void checkOutput(Path output, boolean overwrite) {
        if (exists(output) && !overwrite)
            throw new InvalidOptionValueException("Output file (%s) already exists. Use -overwrite to replace it.".formatted(output));
    }

    private static boolean pointToSameFile(Path in, Path out) {
        try {
            return isSameFile(in, out);
        } catch (IOException e) {
            // Cannot tell the files apart: treat them as different and let the conversion report the real error.
            log.debug("Could not compare input {} and output {}.", in, out, e);
            return false;
        }
    }

}

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
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;

import static java.lang.System.exit;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.FileSystems.getDefault;
import static java.nio.file.Files.*;
import static java.nio.file.Paths.get;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.attribute.PosixFilePermissions.asFileAttribute;
import static java.nio.file.attribute.PosixFilePermissions.fromString;
import static org.jose4j.jwk.JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE;
import static org.jose4j.jwk.JsonWebKey.OutputControlLevel.PUBLIC_ONLY;
import static org.jose4j.jwk.RsaJwkGenerator.generateJwk;

public class JwkGenerator {
    private static final Logger log = LoggerFactory.getLogger(JwkGenerator.class);

    /**
     * Other users must not be able to read a private key.
     */
    private static final Set<PosixFilePermission> OWNER_ONLY = fromString("rw-------");

    /**
     * Windows cannot express {@link #OWNER_ONLY}; there the key file keeps the platform default.
     */
    private static final boolean POSIX_PERMISSIONS = getDefault().supportedFileAttributeViews().contains("posix");

    public static void generateJWK(MembraneCommandLine commandLine) {
        int bits = commandLine.getCommand().getIntOptionValue("b", 2048, 2048, 16384);
        boolean overwrite = commandLine.getCommand().isOptionSet("overwrite");

        // -o is a required option, so the value is always there.
        Path output = get(commandLine.getCommand().getTrimmedOptionValue("o"));
        checkOutput(output, overwrite);

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
            writePrivateJwk(output, rsaJsonWebKey.toJson(INCLUDE_PRIVATE), overwrite);
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
            writePublicJwk(get(output), rsa.toJson(PUBLIC_ONLY), overwrite);
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

    /**
     * Restricts the key file to its owner, which also tightens a laxer file replaced with
     * -overwrite. Called before any key material reaches the file.
     */
    private static void writePrivateJwk(Path output, String jwk, boolean overwrite) throws IOException {
        try (SeekableByteChannel channel = openOutput(output, overwrite, ownerOnlyAttributes())) {
            if (POSIX_PERMISSIONS)
                setPosixFilePermissions(output, OWNER_ONLY);
            writeFully(channel, jwk);
        }
    }

    private static void writePublicJwk(Path output, String jwk, boolean overwrite) throws IOException {
        try (SeekableByteChannel channel = openOutput(output, overwrite)) {
            writeFully(channel, jwk);
        }
    }

    /**
     * Reserves the output file in one step. {@link #checkOutput} runs long before the key is written -
     * generating a 16384 bit key takes a while - so creating the file exclusively is what actually keeps
     * the command from truncating a file it never inspected. It also refuses a dangling symbolic link
     * planted at the path, which {@code exists()} does not report.
     *
     * @throws InvalidOptionValueException if the path was taken after {@link #checkOutput} ran
     */
    private static SeekableByteChannel openOutput(Path output, boolean overwrite, FileAttribute<?>... attributes) throws IOException {
        try {
            return newByteChannel(output, overwrite ? Set.of(CREATE, WRITE, TRUNCATE_EXISTING) : Set.of(CREATE_NEW, WRITE), attributes);
        } catch (FileAlreadyExistsException e) {
            throw new InvalidOptionValueException("Output file (%s) could not be created: the path is already taken.".formatted(output));
        }
    }

    /**
     * @return {@link #OWNER_ONLY} to create the file with, or nothing where the file system has no
     * POSIX permissions
     */
    private static FileAttribute<?>[] ownerOnlyAttributes() {
        if (!POSIX_PERMISSIONS)
            return new FileAttribute<?>[0];
        return new FileAttribute<?>[]{asFileAttribute(OWNER_ONLY)};
    }

    private static void writeFully(SeekableByteChannel channel, String content) throws IOException {
        ByteBuffer buffer = UTF_8.encode(content);
        while (buffer.hasRemaining())
            channel.write(buffer);
    }

}

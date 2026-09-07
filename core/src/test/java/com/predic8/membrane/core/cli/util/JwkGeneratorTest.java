/* Copyright 2026 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.cli.InvalidOptionValueException;
import com.predic8.membrane.core.cli.MembraneCommandLine;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static com.predic8.membrane.core.cli.util.JwkGenerator.*;
import static java.nio.file.Files.*;
import static java.nio.file.attribute.PosixFilePermissions.fromString;
import static org.jose4j.jwk.JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE;
import static org.jose4j.jwk.RsaJwkGenerator.generateJwk;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;

class JwkGeneratorTest {

    @TempDir
    Path dir;

    private Path privateJwk;

    @BeforeEach
    void setUp() throws JoseException, IOException {
        privateJwk = dir.resolve("key.json");
        writeString(privateJwk, generateJwk(2048).toJson(INCLUDE_PRIVATE));
    }

    /**
     * <code>private-jwk-to-public -i k.json -o k.json</code> wrote the public JWK onto its own input
     * and destroyed the private key components, see issue #3219.
     */
    @Test
    void refusesToWriteOntoItsInput() {
        assertEquals("Invalid value for -o: '%s' is the input file. Converting it in place would destroy the private key.".formatted(privateJwk),
                assertThrows(InvalidOptionValueException.class,
                        () -> checkFiles(privateJwk.toString(), privateJwk.toString(), false)).getMessage());
    }

    /**
     * Comparing the two paths as strings is not enough: different spellings of one file must be
     * rejected as well.
     */
    @Test
    void refusesToWriteOntoItsInputSpelledDifferently() throws IOException {
        String indirect = createDirectory(dir.resolve("sub")).resolve("..").resolve("key.json").toString();

        assertThrows(InvalidOptionValueException.class,
                () -> checkFiles(privateJwk.toString(), indirect, false));
    }

    /**
     * Replacing an existing file needs -overwrite, as it does for generate-jwk.
     */
    @Test
    void refusesToReplaceExistingOutput() throws IOException {
        Path output = dir.resolve("public.json");
        writeString(output, "{}");

        assertEquals("Output file (%s) already exists. Use -overwrite to replace it.".formatted(output),
                assertThrows(InvalidOptionValueException.class,
                        () -> checkFiles(privateJwk.toString(), output.toString(), false)).getMessage());
    }

    @Test
    void replacesExistingOutputIfOverwriteIsSet() throws IOException {
        Path output = dir.resolve("public.json");
        writeString(output, "{}");

        assertDoesNotThrow(() -> checkFiles(privateJwk.toString(), output.toString(), true));
    }

    @Test
    void rejectsMissingInput() {
        Path missing = dir.resolve("absent.json");

        assertEquals("Invalid value for -i: '%s' is not a file.".formatted(missing),
                assertThrows(InvalidOptionValueException.class,
                        () -> checkFiles(missing.toString(), dir.resolve("public.json").toString(), false)).getMessage());
    }

    /**
     * generate-jwk reports an existing output file the same way private-jwk-to-public does, and says
     * which flag replaces it. It must do so before spending time on generating a key.
     */
    @Test
    void generateJwkRefusesToReplaceExistingOutputWithoutOverwrite() throws Exception {
        MembraneCommandLine cl = new MembraneCommandLine();
        cl.parse(new String[]{"generate-jwk", "-o", privateJwk.toString()});

        String before = readString(privateJwk);
        assertEquals("Output file (%s) already exists. Use -overwrite to replace it.".formatted(privateJwk),
                assertThrows(InvalidOptionValueException.class, () -> generateJWK(cl)).getMessage());
        assertEquals(before, readString(privateJwk));
    }

    @Test
    void writesPublicKeyAndLeavesInputUntouched() throws IOException {
        String before = readString(privateJwk);
        Path output = dir.resolve("public.json");

        privateJWKtoPublic(privateJwk.toString(), output.toString(), false);

        assertEquals(before, readString(privateJwk));
        for (String privateComponent : new String[]{"\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\""}) {
            assertFalse(readString(output).contains(privateComponent), privateComponent + " leaked into the public JWK");
        }
        assertTrue(readString(output).contains("\"n\""));
    }

    /**
     * The generated file holds the private key components, so it must not be readable by other users.
     */
    @Test
    @EnabledOnOs({LINUX, MAC})
    void generateJwkRestrictsTheKeyFileToItsOwner() throws Exception {
        Path output = dir.resolve("generated.json");
        MembraneCommandLine cl = new MembraneCommandLine();
        cl.parse(new String[]{"generate-jwk", "-o", output.toString()});

        generateJWK(cl);

        assertEquals(fromString("rw-------"), getPosixFilePermissions(output));
    }

    /**
     * Replacing a world-readable file must not leave the private key world-readable.
     */
    @Test
    @EnabledOnOs({LINUX, MAC})
    void generateJwkRestrictsAFileItReplaces() throws Exception {
        Path output = dir.resolve("stale.json");
        writeString(output, "{}");
        setPosixFilePermissions(output, fromString("rw-r--r--"));
        MembraneCommandLine cl = new MembraneCommandLine();
        cl.parse(new String[]{"generate-jwk", "-o", output.toString(), "-overwrite"});

        generateJWK(cl);

        assertEquals(fromString("rw-------"), getPosixFilePermissions(output));
        assertTrue(readString(output).contains("\"d\""));
    }

    /**
     * A dangling symbolic link at the output path passes the existence check - {@code exists()} follows
     * the link and finds nothing - so only creating the file exclusively keeps the private key from being
     * written through the link to wherever it points.
     */
    @Test
    @EnabledOnOs({LINUX, MAC})
    void generateJwkDoesNotWriteThroughADanglingSymbolicLink() throws Exception {
        Path elsewhere = dir.resolve("elsewhere.json");
        Path output = createSymbolicLink(dir.resolve("link.json"), elsewhere);
        MembraneCommandLine cl = new MembraneCommandLine();
        cl.parse(new String[]{"generate-jwk", "-o", output.toString()});

        assertEquals("Output file (%s) could not be created: the path is already taken.".formatted(output),
                assertThrows(InvalidOptionValueException.class, () -> generateJWK(cl)).getMessage());
        assertFalse(exists(elsewhere));
    }

    @Test
    @EnabledOnOs({LINUX, MAC})
    void privateJwkToPublicDoesNotWriteThroughADanglingSymbolicLink() throws IOException {
        Path elsewhere = dir.resolve("elsewhere.json");
        Path output = createSymbolicLink(dir.resolve("link.json"), elsewhere);

        assertEquals("Output file (%s) could not be created: the path is already taken.".formatted(output),
                assertThrows(InvalidOptionValueException.class,
                        () -> privateJWKtoPublic(privateJwk.toString(), output.toString(), false)).getMessage());
        assertFalse(exists(elsewhere));
    }
}

/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.ssl.acme;

import com.google.common.collect.ImmutableList;
import com.predic8.membrane.core.config.security.acme.FileStorage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class AcmeFileStorageEngine implements AcmeSynchronizedStorageEngine {

    private static final Random random = new Random();
    private final String id = UUID.randomUUID().toString();
    private final File base;

    public AcmeFileStorageEngine(FileStorage fileStorage) {
        base = new File(fileStorage.getDir());
    }

    @Override
    public String getAccountKey() {
        try {
            File file = new File(base, "account.jwk.json");
            if (!file.exists())
                return null;
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setAccountKey(String key) {
        File file = new File(base, "account.jwk.json");
        try {
            Files.write(file.toPath(), ImmutableList.of(key));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setKeyPair(String[] hosts, AcmeKeyPair key) {
        try {
            File file = new File(base, "key-" + id(hosts) + "-pub.pem");
            Files.write(file.toPath(), ImmutableList.of(key.getPublicKey()));
            file = new File(base, "key-" + id(hosts) + ".pem");
            Files.write(file.toPath(), ImmutableList.of(key.getPrivateKey()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getPublicKey(String[] hosts) {
        try {
            File file = new File(base, "key-" + id(hosts) + "-pub.pem");
            if (!file.exists())
                return null;
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getPrivateKey(String[] hosts) {
        try {
            File file = new File(base, "key-" + id(hosts) + ".pem");
            if (!file.exists())
                return null;
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setCertChain(String[] hosts, String caChain) {
        try {
            File file = new File(base, "cert-" + id(hosts) + ".pem");
            Files.write(file.toPath(), ImmutableList.of(caChain));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getCertChain(String[] hosts) {
        try {
            File file = new File(base, "cert-" + id(hosts) + ".pem");
            if (!file.exists())
                return null;
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String id(String[] hosts) {
        int i = Arrays.hashCode(hosts);
        if (i < 0)
            i = Integer.MAX_VALUE + i + 1;
        return hosts[0] + "-" + i;
    }

    @Override
    public void setToken(String host, String token) {
        try {
            File file = new File(base, "token-" + host + ".txt");
            Files.write(file.toPath(), ImmutableList.of(token));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getToken(String host) {
        try {
            File file = new File(base, "token-" + host + ".txt");
            if (!file.exists())
                return null;
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getAccountURL() {
        try {
            File file = new File(base, "account-url.txt");
            if (!file.exists())
                return null;
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setAccountURL(String url) {
        File file = new File(base, "account-url.txt");
        try {
            Files.write(file.toPath(), ImmutableList.of(url));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getAccountContacts() {
        try {
            File file = new File(base, "account-contacts.txt");
            if (!file.exists())
                return null;
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setAccountContacts(String contacts) {
        File file = new File(base, "account-contacts.txt");
        try {
            Files.write(file.toPath(), ImmutableList.of(contacts));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getOAL(String[] hosts) {
        try {
            File file = new File(base, "oal-" + id(hosts) + "-current.json");
            if (!file.exists())
                return null;
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setOAL(String[] hosts, String oal) {
        try {
            File file = new File(base, "oal-" + id(hosts) + "-current.json");
            Files.write(file.toPath(), ImmutableList.of(oal));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getOALError(String[] hosts) {
        try {
            File file = new File(base, "oal-" + id(hosts) + "-current-error.json");
            if (!file.exists())
                return null;
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setOALError(String[] hosts, String oalError) {
        try {
            File file = new File(base, "oal-" + id(hosts) + "-current-error.json");
            Files.write(file.toPath(), ImmutableList.of(oalError));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getOALKey(String[] hosts) {
        try {
            File file = new File(base, "oal-" + id(hosts) + "-current-key.json");
            if (!file.exists())
                return null;
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setOALKey(String[] hosts, String oalKey) {
        try {
            File file = new File(base, "oal-" + id(hosts) + "-current-key.json");
            Files.write(file.toPath(), ImmutableList.of(oalKey));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void archiveOAL(String[] hosts) {
        long now = System.currentTimeMillis();
        String id = id(hosts);
        attemptRename("oal-"+id+"-current.json", "oal-"+id+"-" + now + ".json");
        attemptRename("oal-"+id+"-current-error.json", "oal-"+id+"-" + now + "-error.json");
        attemptRename("oal-"+id+"-current-key.json", "oal-"+id+"-" + now + "-key.json");
    }

    private void attemptRename(String f1, String f2) {
        File file1 = new File(base, f1);
        File file2 = new File(base, f2);
        if (file1.exists())
            if (!file1.renameTo(file2))
                throw new RuntimeException("Could not rename file " + file1.getAbsolutePath() + " to " + file2.getAbsolutePath() + " .");
    }


    @Override
    public boolean acquireLease(long durationMillis) {
        File f = new File(base, "lock.txt");
        if (f.exists()) {
            try {
                List<String> lines = Files.readAllLines(f.toPath());
                if (lines.size() != 2) {
                    try {
                        Thread.sleep(random.nextInt(1000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    List<String> lines2 = Files.readAllLines(f.toPath());
                    if (lines.size() == lines2.size()) {
                        f.delete();
                    }
                    return false;
                }
                if (Long.parseLong(lines.get(1)) > System.currentTimeMillis())
                    return false;
                Files.delete(f.toPath());
            } catch (IOException e) {
                return false;
            }
        }
        try {
            Files.write(f.toPath(), ImmutableList.of(id, "" + (System.currentTimeMillis() + durationMillis)), StandardOpenOption.CREATE_NEW);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean prolongLease(long durationMillis) {
        File f = new File(base, "lock.txt");
        if (!f.exists())
            return false;
        try {
            List<String> lines = Files.readAllLines(f.toPath());
            if (lines.size() != 2)
                return false;
            if (!lines.get(0).equals(id))
                return false;
            if (Long.parseLong(lines.get(1)) > System.currentTimeMillis())
                return false;
            Files.write(f.toPath(), ImmutableList.of(id, "" + (System.currentTimeMillis() + durationMillis)), StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void releaseLease() {
        File f = new File(base, "lock.txt");
        if (!f.exists())
            return;
        try {
            List<String> lines = Files.readAllLines(f.toPath());
            if (lines.size() != 2)
                return;
            if (!lines.get(0).equals(id))
                return;
            Files.delete(f.toPath());
        } catch (IOException ignored) {
        }
    }
}

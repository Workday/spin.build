package build.spin.module.maven;

/*-
 * #%L
 * Spin Maven Module
 * %%
 * Copyright (C) 2026 Workday, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import build.base.telemetry.TelemetryRecorder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Decrypts {@code <server><password>} values from {@code ~/.m2/settings.xml} that use Maven's
 * {@code {...}} encrypted-password convention: the value is decrypted with the master password
 * from {@code ~/.m2/settings-security.xml}, which is itself encrypted with Maven's fixed default
 * passphrase. The cipher (SHA-256-derived AES-128/CBC/PKCS5Padding key+IV, framed with an 8-byte
 * salt and a padding-length byte) matches {@code org.sonatype.plexus.components.cipher.PBECipher}
 * bundled with the Maven distribution, verified against real {@code --encrypt-master-password}
 * and {@code --encrypt-password} output.
 */
final class MavenPasswordCipher {

    private static final String DEFAULT_MASTER_PASSPHRASE = "settings.security";
    private static final Pattern ENCRYPTED = Pattern.compile("\\{(.+)}", Pattern.DOTALL);

    private MavenPasswordCipher() {
    }

    /**
     * Resolves a raw {@code <password>} value: returned unchanged if it isn't wrapped in
     * {@code {...}}, otherwise decrypted using the master password from {@code settingsSecurityPath}.
     * Falls back to returning the raw (still-encrypted) value on any failure, with a warning logged,
     * so a misconfigured or missing {@code settings-security.xml} degrades to a clearly-wrong
     * credential rather than throwing.
     */
    static String resolve(final String rawPassword, final Path settingsSecurityPath,
                          final TelemetryRecorder recorder) {
        final Matcher m = ENCRYPTED.matcher(rawPassword);
        if (!m.matches()) {
            return rawPassword;
        }
        try {
            return decrypt(m.group(1), readMasterPassword(settingsSecurityPath));
        } catch (final Exception e) {
            recorder.warn(e, "Failed to decrypt Maven server password using %s; using it as-is",
                settingsSecurityPath);
            return rawPassword;
        }
    }

    private static String readMasterPassword(final Path settingsSecurityPath) throws Exception {
        if (!Files.exists(settingsSecurityPath)) {
            throw new IllegalStateException("No settings-security.xml at " + settingsSecurityPath);
        }
        final XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        try (InputStream in = Files.newInputStream(settingsSecurityPath)) {
            final XMLStreamReader r = factory.createXMLStreamReader(in);
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && "master".equals(r.getLocalName())) {
                    final String raw = r.getElementText();
                    final Matcher m = ENCRYPTED.matcher(raw);
                    if (!m.matches()) {
                        throw new IllegalStateException("<master> in " + settingsSecurityPath + " is not encrypted");
                    }
                    return decrypt(m.group(1), DEFAULT_MASTER_PASSPHRASE);
                }
            }
        }
        throw new IllegalStateException("No <master> element in " + settingsSecurityPath);
    }

    private static String decrypt(final String base64Cipher, final String password) throws Exception {
        final byte[] all = Base64.getDecoder().decode(base64Cipher);
        final byte[] salt = Arrays.copyOfRange(all, 0, 8);
        final int padLen = all[8];
        final byte[] encrypted = Arrays.copyOfRange(all, 9, all.length - padLen);

        final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update(password.getBytes(StandardCharsets.UTF_8));
        sha256.update(salt);
        final byte[] hash = sha256.digest();

        final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,
            new SecretKeySpec(hash, 0, 16, "AES"),
            new IvParameterSpec(hash, 16, 16));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}

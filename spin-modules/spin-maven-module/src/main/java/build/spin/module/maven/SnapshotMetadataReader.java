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

import java.io.InputStream;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads a remote repository's {@code maven-metadata.xml} for a SNAPSHOT artifact, extracting the
 * {@code <snapshot>} timestamp/buildNumber suffix that resolves the SNAPSHOT to its real,
 * downloadable filename.
 */
final class SnapshotMetadataReader {

    private SnapshotMetadataReader() {
    }

    /**
     * Extracts the {@code timestamp-buildNumber} suffix from a {@code maven-metadata.xml} document,
     * or empty if either field is missing or the document isn't well-formed.
     */
    static Optional<String> parseSnapshotSuffix(final InputStream in) {
        try {
            final XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            final XMLStreamReader r = factory.createXMLStreamReader(in);
            String timestamp = null;
            String buildNumber = null;
            boolean inSnapshot = false;
            while (r.hasNext()) {
                final int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    final String name = r.getLocalName();
                    if ("snapshot".equals(name)) {
                        inSnapshot = true;
                    } else if (inSnapshot && "timestamp".equals(name)) {
                        timestamp = r.getElementText();
                    } else if (inSnapshot && "buildNumber".equals(name)) {
                        buildNumber = r.getElementText();
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT
                    && "snapshot".equals(r.getLocalName())) {
                    inSnapshot = false;
                }
            }
            if (timestamp != null && buildNumber != null) {
                return Optional.of(timestamp + "-" + buildNumber);
            }
        } catch (final XMLStreamException e) {
            // fall through
        }
        return Optional.empty();
    }
}

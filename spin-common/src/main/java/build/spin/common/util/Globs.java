package build.spin.common.util;

/*-
 * #%L
 * Spin Common Library
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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Helper methods for working with <a href="https://en.wikipedia.org/wiki/Glob_(programming)">GLOBs</a>.
 *
 * @author brian.oliver
 * @since May-2020
 */
public class Globs {

    /**
     * Private constructor to prevent instantiation.
     */
    private Globs() {
        // empty constructor
    }

    /**
     * Converts a standard POSIX Shell globbing pattern into a regular expression {@link Pattern}, that can be used
     * to recognize strings matching the said glob pattern.
     * <p>
     * See also, the POSIX Shell language:
     * http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html#tag_02_13_01
     * <p>
     * Implementation inspired by the explicitly acknowledged public domain solution here:
     * https://stackoverflow.com/questions/1247772/is-there-an-equivalent-of-java-util-regex-for-glob-type-patterns
     *
     * @param glob the glob pattern
     * @return a regular expression {@link Pattern} for the specified GLOB
     * @throws PatternSyntaxException should converting the GLOB into a {@link Pattern} fail
     */
    public static Pattern toPattern(final String glob)
        throws PatternSyntaxException {
        final StringBuilder builder = new StringBuilder(glob.length());
        int inGroup = 0;
        int inClass = 0;
        int firstIndexInClass = -1;
        final char[] arr = glob.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            final char ch = arr[i];
            switch (ch) {
            case '\\':
                if (++i >= arr.length) {
                    builder.append('\\');
                }
                else {
                    final char next = arr[i];
                    switch (next) {
                    case ',':
                        // escape not needed
                        break;
                    case 'Q':
                    case 'E':
                        // extra escape needed
                        builder.append("\\\\");
                        break;
                    default:
                        builder.append("\\");
                    }
                    builder.append(next);
                }
                break;
            case '*':
                if (inClass == 0) {
                    builder.append(".*");
                }
                else {
                    builder.append('*');
                }
                break;
            case '?':
                if (inClass == 0) {
                    builder.append('.');
                }
                else {
                    builder.append('?');
                }
                break;
            case '[':
                inClass++;
                firstIndexInClass = i + 1;
                builder.append('[');
                break;
            case ']':
                inClass--;
                builder.append(']');
                break;
            case '.':
            case '(':
            case ')':
            case '+':
            case '|':
            case '^':
            case '$':
            case '@':
            case '%':
                if (inClass == 0 || (firstIndexInClass == i && ch == '^')) {
                    builder.append('\\');
                }
                builder.append(ch);
                break;
            case '!':
                if (firstIndexInClass == i) {
                    builder.append('^');
                }
                else {
                    builder.append('!');
                }
                break;
            case '{':
                inGroup++;
                builder.append('(');
                break;
            case '}':
                inGroup--;
                builder.append(')');
                break;
            case ',':
                if (inGroup > 0) {
                    builder.append('|');
                }
                else {
                    builder.append(',');
                }
                break;
            default:
                builder.append(ch);
            }
        }
        return Pattern.compile(builder.toString());
    }
}

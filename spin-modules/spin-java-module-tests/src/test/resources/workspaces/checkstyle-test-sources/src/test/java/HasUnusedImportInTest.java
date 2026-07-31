/*-
 * #%L
 * Spin Java Module Tests
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
import java.util.List;

/**
 * A {@code src/test/java} class with an unused import, to trigger a Checkstyle violation that is
 * only in scope when {@code includeTestSourceDirectory} is honored.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
public class HasUnusedImportInTest {

    public static void main(String[] args) {
        System.out.println("This test source has an unused import to trigger Checkstyle");
    }
}

package build.spin.module.languageserver.utils;

/*-
 * #%L
 * Spin Language Server Module
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/**
 * Utility class for managing JSON.
 *
 * @author drew.malin
 * @since Jan-2023
 */
public class JsonUtils {

    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        // Handle Optional.class
        JSON.registerModule(new Jdk8Module());
    }

    private JsonUtils() {
        // utils
    }

    /**
     * Serializes the provided {@link Object} to a JSON-formatted {@link String}.
     *
     * @param object the {@link Object} to serialized
     * @return the JSON-formatted {@link String} serialization of the provided object
     */
    public static String toJson(final Object object) {
        final String resultJson;
        try {
            resultJson = JSON.writeValueAsString(object);
        }
        catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return resultJson;
    }

    /**
     * Deserializes the provided {@link JsonNode} into a object of type {@link T}.
     *
     * @param jsonNode the {@link JsonNode} to deserialize
     * @param clazz    the {@link Class<T>} of the resultant object
     * @param <T>      the type of the resultant object
     * @return the resultant {@link T}
     */
    public static <T> T fromJson(final Object jsonNode, final Class<T> clazz) {
        try {
            return JSON.convertValue(jsonNode, clazz);
        }
        catch (final IllegalArgumentException e) {
            throw e;
        }
    }

    /**
     * Deserializes the provided {@link String} into a object of type {@link T}.
     *
     * @param json  the {@link String} to deserialize
     * @param clazz the {@link Class<T>} of the resultant object
     * @param <T>   the type of the resultant object
     * @return the resultant {@link T}
     */
    public static <T> T fromJson(final String json, final Class<T> clazz)
        throws JsonProcessingException {
        return JSON.readValue(json, clazz);
    }
}

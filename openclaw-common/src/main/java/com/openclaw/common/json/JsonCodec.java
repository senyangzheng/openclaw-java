package com.openclaw.common.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.openclaw.common.error.CommonErrorCode;
import com.openclaw.common.error.OpenClawException;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Central Fastjson2 facade for the whole project.
 * <p>
 * Business code MUST go through this class instead of instantiating {@code JSON} / {@code ObjectMapper}
 * on its own (see {@code .cursor/plan/05-translation-conventions.md} §11).
 *
 * <p>Global serialization features:
 * <ul>
 *   <li>{@link JSONWriter.Feature#WriteNonStringKeyAsString} &ndash; forgiving map keys</li>
 *   <li>{@link JSONWriter.Feature#IgnoreErrorGetter} &ndash; never fail serialization due to a single getter</li>
 *   <li>{@link JSONReader.Feature#FieldBased} &ndash; read by fields (aligns with record types)</li>
 * </ul>
 */
public final class JsonCodec {

    private static final JSONWriter.Feature[] WRITER_FEATURES = {
        JSONWriter.Feature.WriteNonStringKeyAsString,
        JSONWriter.Feature.IgnoreErrorGetter,
        JSONWriter.Feature.WriteNullStringAsEmpty
    };

    private static final JSONReader.Feature[] READER_FEATURES = {
        JSONReader.Feature.FieldBased,
        JSONReader.Feature.SupportSmartMatch
    };

    private JsonCodec() {
    }

    /** Serialize an object to a compact JSON string. */
    public static String toJson(final Object value) {
        try {
            return JSON.toJSONString(value, WRITER_FEATURES);
        } catch (RuntimeException e) {
            throw new OpenClawException(CommonErrorCode.JSON_SERIALIZE,
                "Failed to serialize object of type " + (value == null ? "null" : value.getClass().getName()), e);
        }
    }

    /** Serialize an object to a pretty JSON string (dev / debug use only). */
    public static String toPrettyJson(final Object value) {
        try {
            return JSON.toJSONString(value, JSONWriter.Feature.PrettyFormat);
        } catch (RuntimeException e) {
            throw new OpenClawException(CommonErrorCode.JSON_SERIALIZE,
                "Failed to serialize object of type " + (value == null ? "null" : value.getClass().getName()), e);
        }
    }

    public static <T> T fromJson(final String json, final Class<T> type) {
        try {
            return JSON.parseObject(json, type, READER_FEATURES);
        } catch (RuntimeException e) {
            throw new OpenClawException(CommonErrorCode.JSON_DESERIALIZE,
                "Failed to deserialize to " + type.getName(), e);
        }
    }

    public static <T> T fromJson(final String json, final Type type) {
        try {
            return JSON.parseObject(json, type, READER_FEATURES);
        } catch (RuntimeException e) {
            throw new OpenClawException(CommonErrorCode.JSON_DESERIALIZE,
                "Failed to deserialize to " + type, e);
        }
    }

    public static <T> T fromJson(final String json, final TypeReference<T> typeRef) {
        try {
            return JSON.parseObject(json, typeRef.getType(), READER_FEATURES);
        } catch (RuntimeException e) {
            throw new OpenClawException(CommonErrorCode.JSON_DESERIALIZE,
                "Failed to deserialize to " + typeRef.getType(), e);
        }
    }

    public static <T> List<T> fromJsonArray(final String json, final Class<T> elementType) {
        try {
            return JSON.parseArray(json, elementType);
        } catch (RuntimeException e) {
            throw new OpenClawException(CommonErrorCode.JSON_DESERIALIZE,
                "Failed to deserialize array of " + elementType.getName(), e);
        }
    }

    public static Map<String, Object> fromJsonMap(final String json) {
        try {
            return JSON.parseObject(json);
        } catch (RuntimeException e) {
            throw new OpenClawException(CommonErrorCode.JSON_DESERIALIZE,
                "Failed to deserialize to Map<String,Object>", e);
        }
    }
}

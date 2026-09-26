package com.chengjing.platform.models;

import com.fasterxml.jackson.databind.JsonNode;

/** Provider-neutral text model interface for interview dialogue and evidence assessment. */
public interface TextModelPort {
    Result completeJson(String ownerId, String purpose, String instruction, Object context);
    record Result(String model, JsonNode content) {}
}

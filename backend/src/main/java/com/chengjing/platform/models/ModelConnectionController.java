package com.chengjing.platform.models;

import com.chengjing.platform.PlatformCaller;
import com.chengjing.shared.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models")
public class ModelConnectionController {
    private final PlatformCaller caller;
    private final TextModelPort gateway;

    public ModelConnectionController(PlatformCaller caller, TextModelPort gateway) {
        this.caller = caller; this.gateway = gateway;
    }

    @PostMapping("/test/{purpose}")
    public ApiResponse<Map<String, String>> test(@PathVariable String purpose) {
        var result = gateway.completeJson(caller.id(), purpose,
                "请返回 {\"connected\":true}，不要添加其他内容。", Map.of("check", "connection"));
        if (!result.content().path("connected").asBoolean(false))
            throw new com.chengjing.platform.PlatformException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "模型已响应但未按要求返回测试结果");
        return ApiResponse.ok(Map.of("status", "UP", "model", result.model()));
    }
}

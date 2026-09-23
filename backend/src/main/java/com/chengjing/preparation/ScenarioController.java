package com.chengjing.preparation;

import com.chengjing.shared.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/preparation")
public class ScenarioController {
    private final ScenarioCatalog catalog;
    public ScenarioController(ScenarioCatalog catalog) { this.catalog = catalog; }

    @GetMapping("/roles")
    public ApiResponse<List<String>> roles() { return ApiResponse.ok(catalog.roles()); }

    @GetMapping("/scenarios")
    public ApiResponse<List<ScenarioCatalog.Scenario>> list(@RequestParam(required = false) String role) {
        return ApiResponse.ok(catalog.list(role));
    }

    @GetMapping("/scenarios/{id}")
    public ResponseEntity<ApiResponse<ScenarioCatalog.Scenario>> detail(@PathVariable String id) {
        return catalog.find(id).map(s -> ResponseEntity.ok(ApiResponse.ok(s)))
            .orElseGet(() -> ResponseEntity.status(404).body(new ApiResponse<>(false, null, "岗位情境不存在")));
    }
}

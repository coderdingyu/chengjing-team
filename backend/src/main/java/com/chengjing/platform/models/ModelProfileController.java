package com.chengjing.platform.models;

import com.chengjing.platform.PlatformCaller;
import com.chengjing.shared.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models")
public class ModelProfileController {
    private final PlatformCaller caller;
    private final ModelProfileService profiles;

    public ModelProfileController(PlatformCaller caller, ModelProfileService profiles) {
        this.caller = caller; this.profiles = profiles;
    }

    @GetMapping
    public ApiResponse<ModelProfileService.Settings> settings() {
        return ApiResponse.ok(profiles.settings(caller.id()));
    }

    @PostMapping
    public ApiResponse<ModelProfileService.View> create(@RequestBody ModelProfileService.Input input) {
        return ApiResponse.ok(profiles.save(caller.id(), null, input));
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelProfileService.View> update(@PathVariable String id, @RequestBody ModelProfileService.Input input) {
        return ApiResponse.ok(profiles.save(caller.id(), id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        profiles.delete(caller.id(), id);
        return ApiResponse.ok(null);
    }

    public record RouteInput(String profileId) {}
    @PutMapping("/routes/{purpose}")
    public ApiResponse<Void> route(@PathVariable String purpose, @RequestBody RouteInput input) {
        profiles.route(caller.id(), purpose, input.profileId());
        return ApiResponse.ok(null);
    }
}

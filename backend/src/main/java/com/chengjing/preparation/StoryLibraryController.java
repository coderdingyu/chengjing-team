package com.chengjing.preparation;

import com.chengjing.shared.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 个人经历素材库接口：列表、搜索与详情都只返回本人素材，别人的素材一律 403。 */
@RestController
@RequestMapping("/api/v1/preparation/stories")
public class StoryLibraryController {
    private final StoryLibraryService service;

    public StoryLibraryController(StoryLibraryService service) {
        this.service = service;
    }

    public record CreateStoryRequest(String title, String content, String source, String role, List<String> tags) {}

    public record UpdateStoryRequest(String title, String content, String source, String role, List<String> tags) {}

    public record ConfirmStoryRequest(Boolean confirmed) {}

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StoryLibraryService.StoryView> create(@RequestBody CreateStoryRequest request) {
        return ApiResponse.ok(service.create(request.title(), request.content(), request.source(),
                request.role(), request.tags()));
    }

    @GetMapping("")
    public ApiResponse<List<StoryLibraryService.StoryView>> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "source", required = false) String source) {
        return ApiResponse.ok(service.search(keyword, source));
    }

    @GetMapping("/{storyId}")
    public ApiResponse<StoryLibraryService.StoryView> detail(@PathVariable String storyId) {
        return ApiResponse.ok(service.require(storyId));
    }

    @PatchMapping("/{storyId}")
    public ApiResponse<StoryLibraryService.StoryView> update(@PathVariable String storyId,
            @RequestBody UpdateStoryRequest request) {
        return ApiResponse.ok(service.update(storyId, request.title(), request.content(), request.source(),
                request.role(), request.tags()));
    }

    @PatchMapping("/{storyId}/confirmation")
    public ApiResponse<StoryLibraryService.StoryView> confirm(@PathVariable String storyId,
            @RequestBody ConfirmStoryRequest request) {
        return ApiResponse.ok(service.confirm(storyId, Boolean.TRUE.equals(request.confirmed())));
    }

    @DeleteMapping("/{storyId}")
    public ApiResponse<Map<String, String>> delete(@PathVariable String storyId) {
        return ApiResponse.ok(Map.of("deletedId", service.delete(storyId)));
    }
}

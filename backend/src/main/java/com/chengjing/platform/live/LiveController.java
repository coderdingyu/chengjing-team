package com.chengjing.platform.live;

import com.chengjing.platform.PlatformCaller;
import com.chengjing.shared.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models/live")
public class LiveController {
    private final PlatformCaller caller;
    private final LiveTicketService tickets;

    public LiveController(PlatformCaller caller, LiveTicketService tickets) {
        this.caller = caller; this.tickets = tickets;
    }

    public record TicketInput(String role, String sessionId, boolean consent) {}
    @PostMapping("/ticket")
    public ApiResponse<LiveTicketService.TicketView> ticket(@RequestBody TicketInput input) {
        if (input == null || !input.consent())
            throw new com.chengjing.platform.PlatformException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "请先同意麦克风声音发送至所选 Live 模型");
        return ApiResponse.ok(tickets.issue(caller.id(), input.role(), input.sessionId()));
    }

    @GetMapping("/transcripts/{sessionId}")
    public ApiResponse<JsonNode> transcript(@PathVariable String sessionId) {
        return ApiResponse.ok(tickets.transcript(caller.id(), sessionId));
    }
}

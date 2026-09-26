package com.chengjing.platform.live;

import com.chengjing.platform.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class LiveSocketHandler extends TextWebSocketHandler {
    private final LiveTicketService tickets;
    private final ObjectMapper mapper;
    private final Map<String, Context> sessions = new ConcurrentHashMap<>();

    public LiveSocketHandler(LiveTicketService tickets, ObjectMapper mapper) {
        this.tickets = tickets; this.mapper = mapper;
    }

    private static class Context {
        final WebSocketSession browser;
        final LiveTicketService.Ticket ticket;
        final Instant started = Instant.now();
        volatile StepLiveSession provider;
        long bytes;
        Context(WebSocketSession browser, LiveTicketService.Ticket ticket) { this.browser = browser; this.ticket = ticket; }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            if (message.getPayloadLength() > 100_000) { send(session, Map.of("type", "error", "message", "音频包过大")); return; }
            JsonNode event = mapper.readTree(message.getPayload());
            String type = event.path("type").asText("");
            Context current = sessions.get(session.getId());
            if ("start".equals(type) && current == null) {
                if (!event.path("consent").asBoolean(false)) throw new IllegalArgumentException("请先同意麦克风使用");
                var ticket = tickets.consume(event.path("ticket").asText(""));
                var context = new Context(new ConcurrentWebSocketSessionDecorator(session, 5000, 1_000_000), ticket);
                sessions.put(session.getId(), context);
                try {
                    context.provider = StepLiveSession.connect(mapper, ticket, event.path("voice").asText("linjiajiejie"),
                            tickets.instructions(ticket), outgoing -> onProviderEvent(context, outgoing));
                } catch (RuntimeException failure) {
                    sessions.remove(session.getId());
                    throw failure;
                }
                return;
            }
            if (current == null || current.provider == null) throw new IllegalArgumentException("请重新连接 Live");
            if (Duration.between(current.started, Instant.now()).toMinutes() > 55) {
                send(current.browser, Map.of("type", "error", "message", "本场 Live 已到时，请重新连接继续"));
                current.provider.close(); return;
            }
            switch (type) {
                case "audio" -> {
                    String audio = event.path("audio").asText("");
                    if (audio.length() > 80_000 || !audio.matches("[A-Za-z0-9+/=]+")) throw new IllegalArgumentException("音频包格式无效");
                    current.bytes += audio.length();
                    if (current.bytes > 40_000_000) throw new IllegalArgumentException("本场音频流已达上限，请重连");
                    current.provider.audio(audio);
                }
                case "interrupt" -> current.provider.interrupt(event.path("playedMs").asInt(0));
                case "mute" -> current.provider.mute(event.path("value").asBoolean(true));
                default -> throw new IllegalArgumentException("Live 事件无效");
            }
        } catch (PlatformException | IllegalArgumentException e) {
            send(session, Map.of("type", "error", "message", e.getMessage()));
        } catch (Exception e) {
            send(session, Map.of("type", "error", "message", "Live 处理失败，请重连"));
        }
    }

    private void onProviderEvent(Context context, Map<String, Object> event) {
        String type = (String) event.get("type");
        if ("candidate.final".equals(type) || "interviewer.final".equals(type))
            tickets.append(context.ticket, "candidate.final".equals(type) ? "candidate" : "interviewer", (String) event.get("text"));
        send(context.browser, event);
    }

    private void send(WebSocketSession session, Map<String, Object> event) {
        try { if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(event))); }
        catch (Exception ignored) { /* Browser disconnect does not erase transcript. */ }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Context context = sessions.remove(session.getId());
        if (context != null && context.provider != null) context.provider.close();
    }
}

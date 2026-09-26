package com.chengjing.platform.live;

import com.chengjing.platform.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;

/** Server-side StepAudio 3 duplex bridge. Provider credentials never reach the browser. */
public class StepLiveSession implements WebSocket.Listener, AutoCloseable {
    private final ObjectMapper mapper;
    private final Consumer<Map<String, Object>> events;
    private final String instructions, voice;
    private final StringBuilder buffer = new StringBuilder();
    private volatile WebSocket socket;
    private volatile boolean ready, responding, closed, muted, cancelled;
    private volatile String responseId = "", itemId = "";
    private CompletableFuture<WebSocket> sendQueue = CompletableFuture.completedFuture(null);

    private StepLiveSession(ObjectMapper mapper, String instructions, String voice,
            Consumer<Map<String, Object>> events) {
        this.mapper = mapper; this.instructions = instructions; this.voice = voice; this.events = events;
    }

    public static StepLiveSession connect(ObjectMapper mapper, LiveTicketService.Ticket ticket, String voice,
            String instructions, Consumer<Map<String, Object>> events) {
        if (!List.of("linjiajiejie", "wenrounansheng").contains(voice))
            throw new PlatformException(HttpStatus.BAD_REQUEST, "所选 Live 声音不可用");
        StepLiveSession session = new StepLiveSession(mapper, instructions, voice, events);
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            session.socket = client.newWebSocketBuilder().header("Authorization", "Bearer " + ticket.connection().key)
                    .buildAsync(URI.create("wss://api.stepfun.com/v1/realtime?model=stepaudio-3-realtime-preview"), session)
                    .orTimeout(18, TimeUnit.SECONDS).join();
            if (session.closed) session.socket.abort();
            return session;
        } catch (Exception e) {
            session.close();
            throw new PlatformException(HttpStatus.BAD_GATEWAY, "Live 连接失败，请检查阶跃模型权限、密钥与网络");
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        if (closed) return CompletableFuture.completedFuture(null);
        buffer.append(data);
        if (buffer.length() > 2_000_000) { fail("Live 返回数据过大，已断开连接"); return CompletableFuture.completedFuture(null); }
        if (last) {
            try { handle(mapper.readTree(buffer.toString())); }
            catch (Exception e) { fail("Live 返回异常，已保存的文字仍可查看"); }
            finally { buffer.setLength(0); }
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    private void handle(JsonNode event) {
        String type = event.path("type").asText();
        String item = event.path("item_id").asText("");
        String rid = event.path("response_id").asText("");
        switch (type) {
            case "session.created" -> send(Map.of("type", "session.update", "session", Map.of(
                    "modalities", List.of("text", "audio"), "instructions", instructions, "voice", voice,
                    "input_audio_format", "pcm16", "output_audio_format", "pcm16",
                    "turn_detection", Map.of("type", "server_vad", "silence_duration_ms", 1600,
                            "prefix_padding_ms", 400))));
            case "session.updated" -> {
                if (!ready) {
                    ready = true;
                    emit(Map.of("type", "ready", "sampleRate", 24000));
                    send(Map.of("type", "conversation.item.create", "item", Map.of("type", "message",
                            "role", "user", "content", List.of(Map.of("type", "input_text",
                                    "text", "请向我提出当前环节的一个面试问题。")))));
                    send(Map.of("type", "response.create"));
                }
            }
            case "input_audio_buffer.speech_started" -> emit(Map.of("type", "speech.started"));
            case "input_audio_buffer.speech_stopped" -> emit(Map.of("type", "speech.stopped"));
            case "conversation.item.input_audio_transcription.completed" -> {
                String text = event.path("transcript").asText("");
                if (!text.isBlank()) emit(Map.of("type", "candidate.final", "text", text));
            }
            case "response.created" -> {
                responding = true; cancelled = false; responseId = event.path("response").path("id").asText(""); itemId = "";
                emit(Map.of("type", "response.started"));
            }
            case "response.audio.delta", "response.output_audio.delta" -> {
                itemId = item;
                String audio = event.path("delta").asText("");
                if (!cancelled && !audio.isBlank()) emit(Map.of("type", "audio.delta", "audio", audio));
            }
            case "response.audio_transcript.delta", "response.output_audio_transcript.delta" -> {
                String text = event.path("delta").asText("");
                if (!cancelled && !text.isBlank()) emit(Map.of("type", "interviewer.partial", "text", text));
            }
            case "response.audio_transcript.done", "response.output_audio_transcript.done" -> {
                String text = event.path("transcript").asText("");
                if (!cancelled && !text.isBlank()) emit(Map.of("type", "interviewer.final", "text", text));
            }
            case "response.done", "response.cancelled" -> {
                responding = false;
                emit(Map.of("type", "response.done"));
                if ("failed".equals(event.path("response").path("status").asText())) fail("Live 本轮生成失败，请重新连接");
            }
            case "conversation.item.input_audio_transcription.failed" ->
                    emit(Map.of("type", "warning", "message", "这段声音未能转写，请重新说一遍；未转写内容不会计分"));
            case "error" -> {
                String eventId = event.path("error").path("event_id").asText("");
                if (!eventId.startsWith("cancel-") && !eventId.startsWith("truncate-"))
                    fail("Live 服务返回错误，请检查模型权限或重新连接");
            }
            default -> { /* Ignore provider metadata and reasoning. */ }
        }
    }

    public void audio(String base64) {
        if (ready && !muted && !closed) send(Map.of("type", "input_audio_buffer.append", "audio", base64));
    }

    public void interrupt(int playedMs) {
        cancelled = true;
        if (responding) send(Map.of("type", "response.cancel", "event_id", "cancel-" + UUID.randomUUID()));
        if (!itemId.isBlank()) send(Map.of("type", "conversation.item.truncate", "event_id", "truncate-" + UUID.randomUUID(),
                "item_id", itemId, "content_index", 0, "audio_end_ms", Math.max(0, Math.min(playedMs, 300_000))));
        responding = false;
    }

    public void mute(boolean value) {
        muted = value;
        if (value) send(Map.of("type", "input_audio_buffer.clear"));
    }

    private synchronized void send(Map<String, Object> event) {
        if (closed || socket == null) return;
        try {
            var body = mapper.writeValueAsString(event);
            sendQueue = sendQueue.thenCompose(ignored -> socket.sendText(body, true))
                    .exceptionally(error -> { fail("Live 网络中断，可重连继续面试"); return null; });
        } catch (Exception e) { fail("Live 发送失败，可重连继续面试"); }
    }

    private void emit(Map<String, Object> event) { if (!closed) events.accept(event); }
    private void fail(String message) { if (!closed) { emit(Map.of("type", "error", "message", message)); close(); } }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
        fail("Live 连接已结束，文字记录仍在，可重新连接");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) { fail("Live 网络中断，文字记录仍在，可重新连接"); }

    @Override
    public void close() { closed = true; if (socket != null) socket.abort(); }
}

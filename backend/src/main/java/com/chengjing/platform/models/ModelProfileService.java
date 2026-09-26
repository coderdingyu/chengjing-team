package com.chengjing.platform.models;

import com.chengjing.platform.PlatformException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account-scoped provider settings. Raw API keys never leave this service. */
@Service
public class ModelProfileService {
    private static final Set<String> PROVIDERS = Set.of("stepfun", "deepseek", "qwen", "openai", "custom");
    private static final Set<String> PURPOSES = Set.of("dialogue", "grading", "voice", "live");
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final EndpointPolicy endpoints;

    public ModelProfileService(JdbcTemplate jdbc, SecretCipher cipher, EndpointPolicy endpoints) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.endpoints = endpoints;
    }

    public record Input(String name, String provider, String baseUrl, String model, String apiKey) {}
    public record View(String id, String name, String provider, String baseUrl, String model, boolean hasKey) {}
    public record Preset(String id, String name, String baseUrl, String sampleModel) {}
    public record Settings(List<View> profiles, Map<String, String> routes, List<Preset> presets) {}

    /** Deliberately not a record: logging must not accidentally include the decrypted key. */
    public static final class Connection {
        public final String provider, baseUrl, model, key;
        private Connection(String provider, String baseUrl, String model, String key) {
            this.provider = provider; this.baseUrl = baseUrl; this.model = model; this.key = key;
        }
    }

    public Settings settings(String ownerId) {
        List<View> profiles = jdbc.query("SELECT id,name,provider,base_url,model_id FROM model_profiles WHERE owner_id=? ORDER BY created_at,id",
                (rs, n) -> new View(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), true), ownerId);
        Map<String, String> routes = new LinkedHashMap<>();
        jdbc.query("SELECT purpose,profile_id FROM model_routes WHERE owner_id=?",
                (RowCallbackHandler) rs -> routes.put(rs.getString(1), rs.getString(2)), ownerId);
        return new Settings(profiles, routes, List.of(
                new Preset("stepfun", "阶跃星辰", "https://api.stepfun.com/v1", "step-5-preview"),
                new Preset("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
                new Preset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
                new Preset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini"),
                new Preset("custom", "自定义兼容站点", "", "")));
    }

    @Transactional
    public View save(String ownerId, String id, Input input) {
        if (input == null || input.name() == null || input.name().isBlank() || input.name().length() > 80
                || input.provider() == null || !PROVIDERS.contains(input.provider()) || input.model() == null
                || !input.model().matches("[A-Za-z0-9_./:@+~-]{1,160}"))
            throw new PlatformException(HttpStatus.BAD_REQUEST, "请填写配置名称、供应商和有效模型 ID");
        String base = endpoints.normalize(input.baseUrl());
        Row old = id == null ? null : owned(ownerId, id);
        if (id == null) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM model_profiles WHERE owner_id=?", Integer.class, ownerId);
            if (count != null && count >= 20) throw new PlatformException(HttpStatus.BAD_REQUEST, "最多保存 20 个模型配置");
            id = UUID.randomUUID().toString();
        }
        String key = input.apiKey() == null ? "" : input.apiKey().strip();
        if (key.isEmpty() && old != null && old.baseUrl.equals(base) && old.provider.equals(input.provider()))
            key = cipher.open(old.secretCipher, binding(ownerId, id, base));
        if (key.isEmpty() || key.length() > 4096 || key.chars().anyMatch(c -> c <= 32 || c > 126))
            throw new PlatformException(HttpStatus.BAD_REQUEST, "请填写 API Key；更改站点或供应商时须重新填写");
        String encrypted = cipher.seal(key, binding(ownerId, id, base));
        if (old == null) jdbc.update("INSERT INTO model_profiles(id,owner_id,name,provider,base_url,model_id,secret_cipher) VALUES(?,?,?,?,?,?,?)",
                id, ownerId, input.name().strip(), input.provider(), base, input.model(), encrypted);
        else jdbc.update("UPDATE model_profiles SET name=?,provider=?,base_url=?,model_id=?,secret_cipher=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND owner_id=?",
                input.name().strip(), input.provider(), base, input.model(), encrypted, id, ownerId);
        return new View(id, input.name().strip(), input.provider(), base, input.model(), true);
    }

    @Transactional
    public void route(String ownerId, String purpose, String profileId) {
        if (!PURPOSES.contains(purpose)) throw new PlatformException(HttpStatus.BAD_REQUEST, "模型用途无效");
        if (profileId != null && !profileId.isBlank()) {
            Row row = owned(ownerId, profileId);
            if ("voice".equals(purpose) && !"stepfun".equals(row.provider))
                throw new PlatformException(HttpStatus.BAD_REQUEST, "传统语音当前只支持阶跃配置");
            if ("live".equals(purpose) && !("stepfun".equals(row.provider)
                    && "https://api.stepfun.com/v1".equals(row.baseUrl)
                    && "stepaudio-3-realtime-preview".equals(row.model)))
                throw new PlatformException(HttpStatus.BAD_REQUEST, "Live 目前仅支持阶跃官方 StepAudio 3 Realtime 配置");
            if (Set.of("dialogue", "grading").contains(purpose) && row.model.toLowerCase().contains("realtime"))
                throw new PlatformException(HttpStatus.BAD_REQUEST, "文本用途请选择文本模型");
        }
        jdbc.update("DELETE FROM model_routes WHERE owner_id=? AND purpose=?", ownerId, purpose);
        if (profileId != null && !profileId.isBlank())
            jdbc.update("INSERT INTO model_routes(owner_id,purpose,profile_id) VALUES(?,?,?)", ownerId, purpose, profileId);
    }

    @Transactional
    public void delete(String ownerId, String id) {
        owned(ownerId, id);
        jdbc.update("DELETE FROM model_profiles WHERE owner_id=? AND id=?", ownerId, id);
    }

    public void deleteOwner(String ownerId) {
        jdbc.update("DELETE FROM model_profiles WHERE owner_id=?", ownerId);
    }

    public Connection resolve(String ownerId, String purpose) {
        if (!PURPOSES.contains(purpose)) throw new IllegalArgumentException("Unknown model purpose");
        var ids = jdbc.query("SELECT profile_id FROM model_routes WHERE owner_id=? AND purpose=?",
                (rs, n) -> rs.getString(1), ownerId, purpose);
        if (ids.isEmpty()) throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, "请先为此用途选择模型配置");
        Row row = owned(ownerId, ids.get(0));
        return new Connection(row.provider, row.baseUrl, row.model,
                cipher.open(row.secretCipher, binding(ownerId, ids.get(0), row.baseUrl)));
    }

    private Row owned(String ownerId, String id) {
        var rows = jdbc.query("SELECT provider,base_url,model_id,secret_cipher FROM model_profiles WHERE owner_id=? AND id=?",
                (rs, n) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), ownerId, id);
        if (rows.isEmpty()) throw new PlatformException(HttpStatus.NOT_FOUND, "模型配置不存在");
        return rows.get(0);
    }

    private String binding(String ownerId, String id, String base) { return ownerId + "|" + id + "|" + base; }
    private record Row(String provider, String baseUrl, String model, String secretCipher) {}
}

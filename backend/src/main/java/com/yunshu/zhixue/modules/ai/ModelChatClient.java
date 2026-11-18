package com.yunshu.zhixue.modules.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yunshu.zhixue.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenAI 兼容对话接口。失败时返回 null，由评分侧降级，不把密钥写进日志。
 */
@Component
public class ModelChatClient {

    private static final Logger log = LoggerFactory.getLogger(ModelChatClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration FAST_FAILURE = Duration.ofSeconds(3);
    private static final long COOLDOWN_MILLIS = 20_000L;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long unavailableUntilMillis;

    public ModelChatClient(AppProperties properties, ObjectMapper objectMapper) {
        AppProperties.Ai ai = properties.getAi();
        this.objectMapper = objectMapper;
        String baseUrl = text(ai.getBaseUrl());
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        this.model = text(ai.getModel());
        this.apiKey = text(ai.getApiKey());
        this.endpoint = baseUrl.isEmpty() ? "" : baseUrl + "/chat/completions";
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        if (enabled()) {
            log.info("AI 评分已接入模型 {}", this.model);
        } else {
            log.info("未配置大模型，简答按关键词降级");
        }
    }

    public boolean enabled() {
        return !endpoint.isEmpty() && !model.isEmpty() && !apiKey.isEmpty();
    }

    public String complete(String systemPrompt, String userPrompt) {
        if (!enabled() || System.currentTimeMillis() < unavailableUntilMillis) {
            return null;
        }
        long started = System.nanoTime();
        try {
            return invoke(systemPrompt, userPrompt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("大模型调用被中断");
            return null;
        } catch (Exception first) {
            if (Duration.ofNanos(System.nanoTime() - started).compareTo(FAST_FAILURE) < 0) {
                try {
                    return invoke(systemPrompt, userPrompt);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.warn("大模型调用被中断");
                    return null;
                } catch (Exception second) {
                    markUnavailable(second);
                    return null;
                }
            }
            markUnavailable(first);
            return null;
        }
    }

    private String invoke(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", 400);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String payload = response.body() == null ? "" : response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(summarize(response.statusCode(), payload));
        }
        JsonNode root = objectMapper.readTree(payload);
        if (root.hasNonNull("error")) {
            throw new IllegalStateException(summarize(response.statusCode(), root.path("error").path("message").asText(payload)));
        }
        String content = messageContent(root.path("choices").path(0).path("message"));
        if (content.isBlank()) {
            throw new IllegalStateException("模型返回内容为空");
        }
        unavailableUntilMillis = 0;
        consecutiveFailures.set(0);
        return content;
    }

    private void markUnavailable(Exception ex) {
        if (consecutiveFailures.incrementAndGet() >= 2) {
            unavailableUntilMillis = System.currentTimeMillis() + COOLDOWN_MILLIS;
        }
        log.warn("大模型调用失败，改为降级评分：{}", ex.getMessage());
    }

    private String messageContent(JsonNode message) {
        JsonNode content = message.get("content");
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText("");
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode part : content) {
                if (part.isTextual()) {
                    builder.append(part.asText());
                } else {
                    builder.append(part.path("text").asText(""));
                }
            }
            return builder.toString();
        }
        return "";
    }

    private String summarize(int status, String body) {
        String text = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (!apiKey.isEmpty()) {
            text = text.replace(apiKey, "***");
        }
        if (text.length() > 160) {
            text = text.substring(0, 160);
        }
        return "HTTP " + status + " " + text;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}

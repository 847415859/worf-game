package com.wolf.backend.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class AiDecisionClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AiDecisionClient(RestTemplate restTemplate, @Value("${wolf.ai-service.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * 调用AI编排服务做出一次决策。失败/超时直接抛出异常，由GameEngine按
     * 文档8.7/8.8节的重试与降级兜底策略处理，本方法不做重试。
     */
    public AiDecideResponse decide(AiDecideRequest request) {
        try {
            AiDecideResponse response = restTemplate.postForObject(baseUrl + "/agent/decide", request, AiDecideResponse.class);
            if (response == null) {
                throw new IllegalStateException("AI编排服务返回空响应");
            }
            return response;
        } catch (Exception e) {
            log.warn("AI决策调用失败: characterId={}, actionType={}, error={}",
                    request.getCharacter() != null ? request.getCharacter().getCharacterId() : null,
                    request.getTask() != null ? request.getTask().getActionType() : null,
                    e.getMessage());
            throw new AiDecisionException("AI决策调用失败", e);
        }
    }

    public static class AiDecisionException extends RuntimeException {
        public AiDecisionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

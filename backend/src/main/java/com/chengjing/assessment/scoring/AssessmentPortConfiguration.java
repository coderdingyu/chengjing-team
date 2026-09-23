package com.chengjing.assessment.scoring;

import com.chengjing.assessment.AssessmentException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssessmentPortConfiguration {
    @Bean
    @ConditionalOnMissingBean
    AssessmentModelPort unavailableAssessmentModelPort() {
        return prompt -> {
            throw AssessmentException.unavailable("评分模型尚未配置，请完成 E03 模型网关连接后重试");
        };
    }

    @Bean
    @ConditionalOnMissingBean
    CurrentUserPort unavailableCurrentUserPort() {
        return () -> {
            throw AssessmentException.unavailable("当前用户接口尚未配置，请完成 A02 身份模块连接");
        };
    }

    @Bean
    @ConditionalOnMissingBean
    InterviewAnswerPort unavailableInterviewAnswerPort() {
        return (userId, answerId) -> {
            throw AssessmentException.unavailable("回答记录接口尚未配置，请完成 C02 文字回答连接");
        };
    }
}

package com.chengjing.preparation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A02 身份模块接入前，B 的个人数据接口无法确认账号归属，
 * 这里提供兜底实现并明确提示依赖，而不是放行一个没有归属的账号。
 */
@Configuration
public class PreparationPortConfiguration {
    @Bean
    @ConditionalOnMissingBean
    PreparationUserPort unavailablePreparationUserPort() {
        return () -> {
            throw PreparationException.unavailable("当前用户接口尚未配置，请完成 A02 身份模块连接");
        };
    }
}

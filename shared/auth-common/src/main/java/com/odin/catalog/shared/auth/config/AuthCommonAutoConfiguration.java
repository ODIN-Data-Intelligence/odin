package com.odin.catalog.shared.auth.config;

import com.odin.catalog.shared.auth.filter.TenantExtractionFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AuthCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantExtractionFilter tenantExtractionFilter() {
        return new TenantExtractionFilter();
    }
}

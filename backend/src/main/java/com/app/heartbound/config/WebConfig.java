package com.app.heartbound.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableSpringDataWebSupport
public class WebConfig implements WebMvcConfigurer {
    
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableResolverCustomizer() {
        return pageableResolver -> {
            pageableResolver.setPageParameterName("page");
            pageableResolver.setSizeParameterName("size");
            // This flag controls whether Page objects are serialized to a DTO
            // for stable serialization structure
            try {
                // Using reflection to avoid direct dependency on newer Spring Data API
                pageableResolver.getClass().getMethod("setPageSerializationUsingDto", boolean.class)
                    .invoke(pageableResolver, true);
            } catch (Exception e) {
                // Fallback for older versions - will still work but with warning
                System.out.println("Note: Using older Spring Data version without PageSerializationMode support");
            }
        };
    }
} 
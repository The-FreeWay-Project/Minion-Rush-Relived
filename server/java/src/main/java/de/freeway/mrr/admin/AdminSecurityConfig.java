package de.freeway.mrr.admin;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class AdminSecurityConfig {

    @Bean
    public FilterRegistrationBean<AdminAuthFilter> adminAuthFilter(AdminSessionManager sessionManager) {
        FilterRegistrationBean<AdminAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AdminAuthFilter(sessionManager));
        registration.addUrlPatterns("/admin/*", "/api/v1/admin/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}

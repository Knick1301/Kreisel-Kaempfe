package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Erlaubte Herkünfte: lokal jeden Port (Vite weicht auf 5174, 5175 … aus, wenn 5173 belegt ist), dazu private
     * Heimnetz-Adressen, damit andere Geräte im LAN mitspielen können (vite --host).
     */
    public static final String[] ORIGINS = {"http://localhost:*", "http://127.0.0.1:*",
            "http://192.168.*.*:*", "http://10.*.*.*:*", "http://172.*.*.*:*"};

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(ORIGINS)
                .allowedMethods("GET", "POST", "PUT", "DELETE");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/register", "/api/auth/login");
    }
}

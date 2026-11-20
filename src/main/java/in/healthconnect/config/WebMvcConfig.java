package in.healthconnect.config;

import in.healthconnect.security.access.ResourceAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Wires the resource-ownership interceptor onto the API. The role/permission check lives in the
// security filter chain; this adds the per-row ownership check on top, for /api/v1 routes only.
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ResourceAccessInterceptor resourceAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(resourceAccessInterceptor).addPathPatterns("/api/v1/**");
    }
}

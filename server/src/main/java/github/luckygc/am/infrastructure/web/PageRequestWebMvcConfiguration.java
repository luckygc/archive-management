package github.luckygc.am.infrastructure.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class PageRequestWebMvcConfiguration implements WebMvcConfigurer {

    private final CursorPageArgumentResolver cursorResolver;
    private final CursorPageTokenValidationInterceptor cursorValidationInterceptor;

    public PageRequestWebMvcConfiguration(
            CursorPageArgumentResolver cursorResolver,
            CursorPageTokenValidationInterceptor cursorValidationInterceptor) {
        this.cursorResolver = cursorResolver;
        this.cursorValidationInterceptor = cursorValidationInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(cursorResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cursorValidationInterceptor);
    }
}

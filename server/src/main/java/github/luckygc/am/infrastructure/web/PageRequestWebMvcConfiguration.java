package github.luckygc.am.infrastructure.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class PageRequestWebMvcConfiguration implements WebMvcConfigurer {

    private final CursorPageRequestArgumentResolver cursorResolver;
    private final OffsetPageRequestArgumentResolver offsetResolver;

    public PageRequestWebMvcConfiguration(
            CursorPageRequestArgumentResolver cursorResolver,
            OffsetPageRequestArgumentResolver offsetResolver) {
        this.cursorResolver = cursorResolver;
        this.offsetResolver = offsetResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(cursorResolver);
        resolvers.add(offsetResolver);
    }
}

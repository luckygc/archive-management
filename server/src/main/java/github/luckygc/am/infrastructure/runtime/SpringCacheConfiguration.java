package github.luckygc.am.infrastructure.runtime;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableCaching
class SpringCacheConfiguration {}

package github.luckygc.am.infrastructure.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;

import github.luckygc.am.common.api.CursorPageTokenCodec;

@Configuration
class CursorPageTokenConfiguration {

    CursorPageTokenConfiguration(CursorPageTokenProperties properties) {
        if (StringUtils.isNotBlank(properties.getSecret())) {
            CursorPageTokenCodec.configureSecret(properties.getSecret());
        }
    }
}

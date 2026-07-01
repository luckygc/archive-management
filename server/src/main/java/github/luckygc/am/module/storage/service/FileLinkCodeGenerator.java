package github.luckygc.am.module.storage.service;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Component;

@Component
public class FileLinkCodeGenerator {

    private static final int CODE_LENGTH = 22;

    public String generate() {
        return RandomStringUtils.secure().nextAlphanumeric(CODE_LENGTH);
    }
}

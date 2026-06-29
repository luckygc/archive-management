package github.luckygc.am.common.cleanup;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExpiredDataCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpiredDataCleanupService.class);

    private final List<ExpiredDataCleaner> cleaners;

    public ExpiredDataCleanupService(List<ExpiredDataCleaner> cleaners) {
        this.cleaners = List.copyOf(cleaners);
    }

    public List<ExpiredDataCleanupResult> cleanupExpiredData() {
        LocalDateTime now = LocalDateTime.now();
        return cleaners.stream().map(cleaner -> cleanup(cleaner, now)).toList();
    }

    private ExpiredDataCleanupResult cleanup(ExpiredDataCleaner cleaner, LocalDateTime now) {
        try {
            return cleaner.cleanupExpired(now);
        } catch (RuntimeException ex) {
            LOGGER.warn("过期数据清理失败：{}", cleaner.name(), ex);
            return new ExpiredDataCleanupResult(cleaner.name(), 0);
        }
    }
}

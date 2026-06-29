package github.luckygc.am.common.cleanup;

import java.time.LocalDateTime;

public interface ExpiredDataCleaner {

    String name();

    ExpiredDataCleanupResult cleanupExpired(LocalDateTime now);
}

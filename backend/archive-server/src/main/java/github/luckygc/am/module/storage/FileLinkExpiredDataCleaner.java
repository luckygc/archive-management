package github.luckygc.am.module.storage;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.cleanup.ExpiredDataCleaner;
import github.luckygc.am.common.cleanup.ExpiredDataCleanupResult;
import github.luckygc.am.module.storage.repository.FileLinkDataRepository;

@Component
public class FileLinkExpiredDataCleaner implements ExpiredDataCleaner {

    private static final String CLEANER_NAME = "file_link";

    private final FileLinkDataRepository fileLinkRepository;

    public FileLinkExpiredDataCleaner(FileLinkDataRepository fileLinkRepository) {
        this.fileLinkRepository = fileLinkRepository;
    }

    @Override
    public String name() {
        return CLEANER_NAME;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public ExpiredDataCleanupResult cleanupExpired(LocalDateTime now) {
        return new ExpiredDataCleanupResult(CLEANER_NAME, fileLinkRepository.deleteExpired(now));
    }
}

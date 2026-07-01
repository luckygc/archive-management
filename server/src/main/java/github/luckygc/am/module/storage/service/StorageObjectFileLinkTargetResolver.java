package github.luckygc.am.module.storage.service;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.service.FileLinkService.FileLinkTarget;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDownload;

@Service
public class StorageObjectFileLinkTargetResolver implements FileLinkTargetResolver {

    private final StorageObjectService storageObjectService;

    public StorageObjectFileLinkTargetResolver(StorageObjectService storageObjectService) {
        this.storageObjectService = storageObjectService;
    }

    @Override
    public FileLinkTargetType targetType() {
        return FileLinkTargetType.STORAGE_OBJECT;
    }

    @Override
    public StorageObjectDownload open(FileLinkTarget target, @Nullable Long userId) {
        return storageObjectService.openObject(target.targetId());
    }
}

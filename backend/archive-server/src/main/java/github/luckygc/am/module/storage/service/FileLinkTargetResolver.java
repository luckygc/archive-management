package github.luckygc.am.module.storage.service;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.service.FileLinkService.FileLinkTarget;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDownload;

public interface FileLinkTargetResolver {

    FileLinkTargetType targetType();

    StorageObjectDownload open(FileLinkTarget target, @Nullable Long userId);
}

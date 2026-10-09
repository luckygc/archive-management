package github.luckygc.am.module.archive.item.service;

import jakarta.data.Limit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.ArchiveVolume;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveVolumeDataRepository;

/** 条目和案卷的业务库归属读写；变更权限、库间转换和历史仍由业务库用例编排。 */
@Service
public class ArchiveRepositoryOwnershipService {

    private final ArchiveItemDataRepository itemRepository;
    private final ArchiveVolumeDataRepository volumeRepository;

    public ArchiveRepositoryOwnershipService(
            ArchiveItemDataRepository itemRepository,
            ArchiveVolumeDataRepository volumeRepository) {
        this.itemRepository = itemRepository;
        this.volumeRepository = volumeRepository;
    }

    @Transactional(readOnly = true)
    public boolean isRepositoryReferenced(Long repositoryId) {
        Limit one = Limit.of(1);
        return !itemRepository.findByRepositoryId(repositoryId, one).isEmpty()
                || !volumeRepository.findByRepositoryId(repositoryId, one).isEmpty();
    }

    @Transactional(readOnly = true)
    public ArchiveItem getRequiredItem(Long itemId) {
        return itemRepository
                .findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "档案条目不存在"));
    }

    @Transactional(readOnly = true)
    public ArchiveVolume getRequiredVolume(Long volumeId) {
        return volumeRepository
                .findById(volumeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "案卷不存在"));
    }

    @Transactional
    public void changeItemRepository(ArchiveItem item, Long repositoryId) {
        item.setRepositoryId(repositoryId);
        itemRepository.update(item);
    }

    @Transactional
    public void changeVolumeRepository(ArchiveVolume volume, Long repositoryId) {
        volume.setRepositoryId(repositoryId);
        volumeRepository.update(volume);
    }
}

package github.luckygc.am.architecture.fixtures.archive.item.service;

import github.luckygc.am.architecture.fixtures.archive.item.repository.ItemRepository;
import github.luckygc.am.architecture.fixtures.archive.mapper.SharedMapper;
import github.luckygc.am.architecture.fixtures.archive.mapper.SharedQuery;
import github.luckygc.am.architecture.fixtures.archive.metadata.service.MetadataService;

public record OwnedDataAccessClient(
        ItemRepository repository,
        MetadataService metadataService,
        SharedMapper mapper,
        SharedQuery query) {}

package github.luckygc.am.architecture.fixtures.archive.item.service;

import github.luckygc.am.architecture.fixtures.archive.metadata.mapper.MetadataMapper;
import github.luckygc.am.architecture.fixtures.archive.metadata.repository.MetadataRepository;

public record CrossSubdomainDataAccessClient(
        MetadataRepository repository, MetadataMapper mapper) {}

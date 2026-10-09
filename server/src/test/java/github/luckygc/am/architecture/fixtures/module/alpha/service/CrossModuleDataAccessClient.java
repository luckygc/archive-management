package github.luckygc.am.architecture.fixtures.module.alpha.service;

import github.luckygc.am.architecture.fixtures.module.beta.mapper.BetaMapper;
import github.luckygc.am.architecture.fixtures.module.beta.repository.BetaRepository;

public record CrossModuleDataAccessClient(BetaRepository repository, BetaMapper mapper) {}

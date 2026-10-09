package github.luckygc.am.architecture.fixtures.module.alpha.service;

import github.luckygc.am.architecture.fixtures.module.alpha.repository.AlphaRepository;
import github.luckygc.am.architecture.fixtures.module.beta.service.BetaService;

public record SameModuleDataAccessClient(AlphaRepository repository, BetaService service) {}

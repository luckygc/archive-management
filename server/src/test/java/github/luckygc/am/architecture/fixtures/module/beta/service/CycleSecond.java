package github.luckygc.am.architecture.fixtures.module.beta.service;

import github.luckygc.am.architecture.fixtures.module.alpha.service.CycleFirst;

public record CycleSecond(CycleFirst first) {}

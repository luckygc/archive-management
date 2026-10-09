package github.luckygc.am.architecture.fixtures.module.alpha.service;

import github.luckygc.am.architecture.fixtures.module.beta.service.CycleSecond;

public record CycleFirst(CycleSecond second) {}

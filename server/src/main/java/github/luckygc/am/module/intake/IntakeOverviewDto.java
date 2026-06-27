package github.luckygc.am.module.intake;

public record IntakeOverviewDto(
        boolean externalConnectionConfigured, String status, String message) {}

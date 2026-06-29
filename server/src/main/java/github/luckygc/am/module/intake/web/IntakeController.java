package github.luckygc.am.module.intake.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.module.intake.IntakeOverviewDto;
import github.luckygc.am.module.intake.service.IntakeService;

@RestController
public class IntakeController {

    private final IntakeService intakeService;

    public IntakeController(IntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @GetMapping("/api/v1/intake")
    public IntakeOverviewDto getOverview() {
        return intakeService.getOverview();
    }
}

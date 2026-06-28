package github.luckygc.am.module.intake;

import org.springframework.stereotype.Service;

@Service
public class IntakeService {

    public static final String STATUS_NOT_CONFIGURED = "not_configured";

    public IntakeOverviewDto getOverview() {
        return new IntakeOverviewDto(false, STATUS_NOT_CONFIGURED, "归档接收入口已启用，暂未对接外部系统");
    }
}

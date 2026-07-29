package github.luckygc.am.module.intake.service;

import org.springframework.stereotype.Service;

import github.luckygc.am.module.intake.IntakeOverviewDto;

@Service
public class IntakeService {

    public static final String STATUS_LOCAL_PACKAGE_AVAILABLE = "local_package_available";

    public IntakeOverviewDto getOverview() {
        return new IntakeOverviewDto(
                false, STATUS_LOCAL_PACKAGE_AVAILABLE, "本地档案信息包接收可用；暂未配置 NAS、SFTP、HTTP 等外部连接");
    }
}

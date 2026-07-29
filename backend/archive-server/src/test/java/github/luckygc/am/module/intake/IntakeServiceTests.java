package github.luckygc.am.module.intake;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.intake.service.IntakeService;

@DisplayName("归档接收服务")
class IntakeServiceTests {

    private final IntakeService intakeService = new IntakeService();

    @Test
    @DisplayName("入口概览声明本地信息包可用且暂未配置外部连接")
    void getOverviewShouldExposeLocalPackageEntry() {
        IntakeOverviewDto overview = intakeService.getOverview();

        assertThat(overview.externalConnectionConfigured()).isFalse();
        assertThat(overview.status()).isEqualTo(IntakeService.STATUS_LOCAL_PACKAGE_AVAILABLE);
        assertThat(overview.message()).contains("本地档案信息包接收可用").contains("外部连接");
    }
}

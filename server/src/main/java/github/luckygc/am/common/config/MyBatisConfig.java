package github.luckygc.am.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("github.luckygc.am.module")
public class MyBatisConfig {
}

package github.luckygc.am.common.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "github.luckygc.am.module", annotationClass = Mapper.class)
public class MyBatisConfig {
}

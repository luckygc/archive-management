package github.luckygc.am.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "github.luckygc.am", annotationClass = Mapper.class)
public class MyBatisConfig {}

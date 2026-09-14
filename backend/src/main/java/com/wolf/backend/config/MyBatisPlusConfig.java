package com.wolf.backend.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.wolf.backend.mapper")
public class MyBatisPlusConfig {
}

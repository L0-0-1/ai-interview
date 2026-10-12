package com.study.interview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 启动类必须在根包下:@SpringBootApplication 隐含 @ComponentScan,
// 默认只扫描"本包 + 子包",config/control 等子包里的 Bean 才能被注册
@SpringBootApplication
@MapperScan("com.study.interview.mapper")
public class MyaiInterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyaiInterviewApplication.class, args);
    }

}

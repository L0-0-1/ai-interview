package com.study.interview;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// @SpringBootTest 会从当前测试类所在包往上找 @SpringBootApplication,
// 所以测试类和启动类必须在同一个根包下,否则加载不到上下文
@SpringBootTest
class MyaiInterviewApplicationTests {

    @Test
    void contextLoads() {
    }

}

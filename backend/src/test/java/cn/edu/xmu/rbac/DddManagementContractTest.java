package cn.edu.xmu.rbac;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.backend-strategy=ddd")
class DddManagementContractTest extends ManagementContract {}

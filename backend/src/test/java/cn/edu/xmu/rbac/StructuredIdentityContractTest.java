package cn.edu.xmu.rbac;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.backend-strategy=structured")
class StructuredIdentityContractTest extends IdentityContract {}

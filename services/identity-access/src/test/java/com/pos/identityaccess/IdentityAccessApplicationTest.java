package com.pos.identityaccess;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
@SuppressWarnings("removal")
class IdentityAccessApplicationTest {

    @MockBean
    private DataSource dataSource;

    @Test
    void shouldLoadApplicationContext_whenP0ServiceIsBootstrapped() {
        assertThat(true).isTrue();
    }
}

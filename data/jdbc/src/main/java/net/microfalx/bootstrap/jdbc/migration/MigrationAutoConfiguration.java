package net.microfalx.bootstrap.jdbc.migration;

import net.microfalx.bootstrap.jdbc.support.DatabaseService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MigrationAutoConfiguration {

    @Bean
    public MigrationInitializer createInitializer(DatabaseService databaseService, javax.sql.DataSource dataSource, MigrationProperties properties) {
        return new MigrationInitializer(databaseService, dataSource, properties);
    }

}

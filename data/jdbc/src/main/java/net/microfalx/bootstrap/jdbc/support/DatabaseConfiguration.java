package net.microfalx.bootstrap.jdbc.support;

import net.microfalx.database.Database;
import net.microfalx.database.QueryProvider;
import net.microfalx.database.Schema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseConfiguration {

    @Bean
    public Database database(DatabaseService databaseService) {
        return databaseService.getDefaultDatabase();
    }

    @Bean
    public Schema schema(DatabaseService databaseService) {
        return databaseService.getDefaultDatabase().getSchema();
    }

    @Bean
    public QueryProvider queryProvider(Database database) {
        return database.getQueryProvider();
    }
}

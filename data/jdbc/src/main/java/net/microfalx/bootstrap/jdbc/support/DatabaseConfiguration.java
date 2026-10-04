package net.microfalx.bootstrap.jdbc.support;

import net.microfalx.bootstrap.jdbc.util.JdbcStorage;
import net.microfalx.configuration.ConfigurationService;
import net.microfalx.registry.RegistryService;
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
        return new QueryProviderImpl(database);
    }

    @Bean
    public JdbcStorage jdbcStorage(QueryProvider queryProvider) {
        JdbcStorage jdbcStorage = new JdbcStorage(queryProvider);
        RegistryService.getInstance().setStorage(jdbcStorage);
        ConfigurationService.getInstance().registerMetadata();
        return jdbcStorage;
    }
}

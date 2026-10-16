package net.microfalx.bootstrap.jdbc.migration;

import net.microfalx.configuration.ConfigurationService;
import net.microfalx.database.DatabaseService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class MigrationInitializer implements InitializingBean {

    private final DataSource dataSource;
    private final MigrationProperties properties;

    public MigrationInitializer(DataSource dataSource, MigrationProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        registerDataSource();
        migrate();
        postMigration();
    }

    private void registerDataSource() {
        net.microfalx.database.DataSource ds = net.microfalx.database.DataSource.create("main", "Main", dataSource);
        getDatabaseService().registerDataSource(ds);
    }

    private void migrate() {
        ConfigurationService.getInstance().registerMetadata();
    }

    private void postMigration() {
        ConfigurationService.getInstance().registerMetadata();
    }

    private DatabaseService getDatabaseService() {
        return DatabaseService.getInstance();
    }
}

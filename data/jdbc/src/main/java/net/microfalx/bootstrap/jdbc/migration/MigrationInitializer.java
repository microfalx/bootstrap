package net.microfalx.bootstrap.jdbc.migration;

import net.microfalx.bootstrap.jdbc.support.DatabaseService;
import net.microfalx.configuration.ConfigurationService;
import net.microfalx.database.migration.Session;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class MigrationInitializer implements InitializingBean {

    private final DatabaseService databaseService;
    private final DataSource dataSource;
    private final MigrationProperties properties;

    public MigrationInitializer(DatabaseService databaseService, DataSource dataSource, MigrationProperties properties) {
        this.databaseService = databaseService;
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        migrate();
        postMigration();
    }

    private void migrate() {
        Session session = new Session(databaseService.getDataSource().orElseThrow());
        session.execute();
    }

    private void postMigration() {
        ConfigurationService.getInstance().registerMetadata();
    }
}

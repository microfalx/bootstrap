package net.microfalx.bootstrap.system.database;

import net.microfalx.bootstrap.dataset.DataSetFactory;
import net.microfalx.bootstrap.dataset.PojoDataSet;
import net.microfalx.bootstrap.model.Filter;
import net.microfalx.bootstrap.model.Metadata;
import net.microfalx.bootstrap.model.PojoField;
import net.microfalx.lang.annotation.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Provider
public class SessionDataSet extends PojoDataSet<Session, PojoField<Session>, String> {

    private net.microfalx.database.DatabaseService databaseService;

    public SessionDataSet(DataSetFactory<Session, PojoField<Session>, String> factory,
                          Metadata<Session, PojoField<Session>, String> metadata) {
        super(factory, metadata);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        databaseService = net.microfalx.database.DatabaseService.getInstance();
    }

    @Override
    protected Optional<Session> doFindById(String id) {
        try {
            return Optional.ofNullable(Session.from(databaseService.findSession(id).orElse(null)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    protected Page<Session> doFindAll(Pageable pageable, Filter filterable) {
        List<Session> models = databaseService.getSessions(true).stream()
                .filter(session -> !session.isSystem())
                .map(Session::from).toList();
        return getPage(models, pageable, filterable);
    }
}

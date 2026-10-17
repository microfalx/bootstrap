package net.microfalx.bootstrap.system.database;

import net.microfalx.bootstrap.dataset.DataSetFactory;
import net.microfalx.bootstrap.dataset.PojoDataSet;
import net.microfalx.bootstrap.model.Filter;
import net.microfalx.bootstrap.model.Metadata;
import net.microfalx.bootstrap.model.PojoField;
import net.microfalx.database.DatabaseService;
import net.microfalx.lang.annotation.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Provider
public class TransactionDataSet extends PojoDataSet<Transaction, PojoField<Transaction>, String> {

    private net.microfalx.database.DatabaseService databaseService;

    public TransactionDataSet(DataSetFactory<Transaction, PojoField<Transaction>, String> factory,
                              Metadata<Transaction, PojoField<Transaction>, String> metadata) {
        super(factory, metadata);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        databaseService = DatabaseService.getInstance();
    }

    @Override
    protected Optional<Transaction> doFindById(String id) {
        try {
            return Optional.ofNullable(Transaction.from(databaseService.findTransaction(id).orElse(null)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    protected Page<Transaction> doFindAll(Pageable pageable, Filter filterable) {
        List<Transaction> models = databaseService.getTransactions(true).stream()
                .map(Transaction::from).toList();
        return getPage(models, pageable, filterable);
    }
}

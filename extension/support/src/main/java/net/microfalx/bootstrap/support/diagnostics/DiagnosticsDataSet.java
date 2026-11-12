package net.microfalx.bootstrap.support.diagnostics;

import net.microfalx.bootstrap.dataset.DataSetFactory;
import net.microfalx.bootstrap.dataset.PojoDataSet;
import net.microfalx.bootstrap.diagnostics.DiagnosticsService;
import net.microfalx.bootstrap.model.Filter;
import net.microfalx.bootstrap.model.Metadata;
import net.microfalx.bootstrap.model.PojoField;
import net.microfalx.lang.annotation.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Provider
public class DiagnosticsDataSet extends PojoDataSet<Diagnostics, PojoField<Diagnostics>, String> {

    public DiagnosticsDataSet(DataSetFactory<Diagnostics, PojoField<Diagnostics>, String> factory,
                              Metadata<Diagnostics, PojoField<Diagnostics>, String> metadata) {
        super(factory, metadata);
    }

    @Override
    protected Optional<Diagnostics> doFindById(String id) {
        return getService(DiagnosticsService.class).getDiagnostics(id).map(Diagnostics::from);
    }

    @Override
    protected Page<Diagnostics> doFindAll(Pageable pageable, Filter filterable) {
        List<Diagnostics> diagnostics = getService(DiagnosticsService.class).getDiagnostics().stream()
                .map(Diagnostics::from).collect(Collectors.toList());
        return getPage(diagnostics, pageable, filterable);
    }
}

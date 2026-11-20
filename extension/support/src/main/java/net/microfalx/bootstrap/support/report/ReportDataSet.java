package net.microfalx.bootstrap.support.report;

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
public class ReportDataSet extends PojoDataSet<Report, PojoField<Report>, String> {

    public ReportDataSet(DataSetFactory<Report, PojoField<Report>, String> factory,
                         Metadata<Report, PojoField<Report>, String> metadata) {
        super(factory, metadata);
    }

    @Override
    protected Optional<Report> doFindById(String id) {
        return getService(ReportService.class).getContent(id).map(Report::from);
    }

    @Override
    protected Page<Report> doFindAll(Pageable pageable, Filter filterable) {
        List<Report> reports = getService(ReportService.class).getContents().stream().map(Report::from).toList();
        return getPage(reports, pageable, filterable);
    }
}

package net.microfalx.bootstrap.support.mail;

import net.microfalx.bootstrap.dataset.DataSetFactory;
import net.microfalx.bootstrap.dataset.PojoDataSet;
import net.microfalx.bootstrap.mail.MailService;
import net.microfalx.bootstrap.model.Filter;
import net.microfalx.bootstrap.model.Metadata;
import net.microfalx.bootstrap.model.PojoField;
import net.microfalx.lang.annotation.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Provider
public class MailDataSet extends PojoDataSet<Mail, PojoField<Mail>, String> {

    public MailDataSet(DataSetFactory<Mail, PojoField<Mail>, String> factory, Metadata<Mail, PojoField<Mail>, String> metadata) {
        super(factory, metadata);
    }

    @Override
    protected Optional<Mail> doFindById(String id) {
        return getService(MailService.class).getMail(id).map(Mail::from);
    }

    @Override
    protected Page<Mail> doFindAll(Pageable pageable, Filter filterable) {
        List<Mail> mails = getService(MailService.class).getMails().stream().map(Mail::from).toList();
        return getPage(mails, pageable, filterable);
    }
}

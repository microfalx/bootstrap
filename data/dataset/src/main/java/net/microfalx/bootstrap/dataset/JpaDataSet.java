package net.microfalx.bootstrap.dataset;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import net.microfalx.bootstrap.model.ComparisonExpression;
import net.microfalx.bootstrap.model.Filter;
import net.microfalx.bootstrap.model.JpaField;
import net.microfalx.bootstrap.model.Metadata;
import net.microfalx.lang.convert.InvalidDataTypeExpression;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.LazyInitializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;

import static net.microfalx.lang.AnnotationUtils.getAnnotation;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;

/**
 * A data set for JPA entities.
 */
public class JpaDataSet<M, ID> extends PojoDataSet<M, JpaField<M>, ID> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSetService.class);

    JpaRepository<M, ID> repository;

    public JpaDataSet(DataSetFactory<M, JpaField<M>, ID> factory, Metadata<M, JpaField<M>, ID> metadata) {
        super(factory, metadata);
    }

    @Override
    public void validate(Filter filter) {
        super.validate(filter);
        Specification<M> specification = createSpecification(filter);
        if (specification != null) {
            EntityManager entityManager = getService(EntityManager.class);
            CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            CriteriaQuery<M> query = criteriaBuilder.createQuery(getMetadata().getModel());
            try {
                specification.toPredicate(query.from(getMetadata().getModel()), query, criteriaBuilder);
            } catch (InvalidDataTypeExpression e) {
                LOGGER.warn("Validation of filter for '{}', filter '{}' has failed with '{}'", getName(), filter, getRootCauseDescription(e));
                throw new DataSetException(e.getMessage(), e);
            }
        }
    }

    @Override
    public <S extends M> List<S> saveAll(Iterable<S> entities) {
        try {
            return repository.saveAll(entities);
        } catch (Exception e) {
            return handleException("save all", e);
        }
    }

    @Override
    protected List<M> doFindAll() {
        return repository.findAll();
    }

    @Override
    protected List<M> doFindAllById(Iterable<ID> ids) {
        return repository.findAllById(ids);
    }

    @Override
    protected Optional<M> doFindById(ID id) {
        return repository.findById(id);
    }

    @Override
    protected boolean doExistsById(ID id) {
        return repository.existsById(id);
    }

    @Override
    protected long doCount() {
        return repository.count();
    }

    @Override
    protected List<M> doFindAll(Sort sort) {
        return repository.findAll(sort);
    }

    @Override
    protected Page<M> doFindAll(Pageable pageable) {
        LOGGER.info("Find all for '{}', page '{}'", getName(), pageable);
        return repository.findAll(pageable);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Page<M> doFindAll(Pageable pageable, Filter filterable) {
        Specification<M> specification = createSpecification(filterable);
        if (specification != null) {
            LOGGER.info("Find all for '{}, page '{}',  filter '{}' specification '{}'", getName(), pageable, filterable, specification);
            JpaSpecificationExecutor<M> executor = (JpaSpecificationExecutor<M>) repository;
            return executor.findAll(specification, pageable);
        } else {
            return findAll(pageable);
        }
    }

    @Override
    protected Optional<M> doFindByDisplayValue(String displayValue) {
        List<JpaField<M>> nameFields = getMetadata().getNameFields();
        if (nameFields.isEmpty()) return Optional.empty();
        Filter filter = Filter.create(ComparisonExpression.eq(nameFields.iterator().next().getName(), displayValue));
        Page<M> page = doFindAll(Pageable.ofSize(1), filter);
        List<M> content = page.getContent();
        Optional<M> value = content.isEmpty() ? Optional.empty() : Optional.of(content.get(0));
        LOGGER.info("Located value '{}' by display value '{}'", value, displayValue);
        return value;
    }

    @Override
    protected <S extends M> S doSave(S model) {
        try {
            return repository.save(model);
        } catch (Exception e) {
            return handleException("save", e);
        }
    }

    @Override
    protected void doDeleteById(ID id) {
        try {
            repository.deleteById(id);
        } catch (Exception e) {
            handleException("delete by id", e);
        }
    }

    @Override
    protected void doDelete(M model) {
        try {
            repository.delete(model);
        } catch (Exception e) {
            handleException("delete", e);
        }
    }

    @Override
    protected void doDeleteAllById(Iterable<? extends ID> ids) {
        try {
            repository.deleteAllById(ids);
        } catch (Exception e) {
            handleException("delete all by id", e);
        }
    }

    @Override
    protected void doDeleteAll(Iterable<? extends M> models) {
        try {
            repository.deleteAll(models);
        } catch (Exception e) {
            handleException("delete all", e);
        }
    }

    @Override
    protected void doDeleteAll() {
        try {
            repository.deleteAll();
        } catch (Exception e) {
            handleException("delete all", e);
        }
    }

    @Override
    public void detach(M model) {
        super.detach(model);
        Hibernate.initialize(model);
        for (JpaField<M> field : getMetadata().getFields()) {
            Object value = field.get(model);
            if (value instanceof LazyInitializable) {
                Hibernate.initialize(value);
            }
        }
    }

    void setRepository(JpaRepository<M, ID> repository) {
        this.repository = repository;
    }

    private <T> T handleException(String action, Throwable throwable) {
        if (throwable instanceof SQLIntegrityConstraintViolationException || throwable instanceof DataIntegrityViolationException) {
            throw new DataSetConstraintViolationException("Failed to executed'" + action + "' due to constraints violation", throwable);
        } else {
            return rethrowExceptionAndReturn(throwable);
        }
    }

    private Specification<M> createSpecification(Filter filter) {
        boolean hasFilters = filter != null && !filter.isEmpty();
        if (hasFilters && !(repository instanceof JpaSpecificationExecutor)) {
            throw new DataSetException("JPA Data Set " + getMetadata().getName() + "' data was requested with a filter " +
                    "but the repository does not implement JpaSpecificationExecutor");
        }
        if (repository instanceof JpaSpecificationExecutor && hasFilters) {
            return new JpaSpecificationBuilder<>(getDataSetService(), getMetadata(), filter).build();
        } else {
            return null;
        }
    }

    public static class Factory<M, ID> extends PojoDataSetFactory<M, JpaField<M>, ID> {

        @Override
        protected AbstractDataSet<M, JpaField<M>, ID> doCreate(Metadata<M, JpaField<M>, ID> metadata) {
            return new JpaDataSet<>(this, metadata);
        }

        @SuppressWarnings("unchecked")
        @Override
        void update(AbstractDataSet<M, JpaField<M>, ID> dataSet, Object... parameters) {
            super.update(dataSet, parameters);
            JpaRepository<M, ID> repository = find(JpaRepository.class, parameters);
            if (repository == null) {
                repository = (JpaRepository<M, ID>) dataSetService.getRepository(dataSet.getMetadata().getModel());
            }
            ((JpaDataSet<M, JpaField<M>>) dataSet).setRepository((JpaRepository<M, JpaField<M>>) repository);
        }

        @Override
        public boolean supports(Metadata<M, JpaField<M>, ID> metadata) {
            return getAnnotation(metadata.getModel(), Entity.class) != null;
        }
    }
}

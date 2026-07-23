package net.microfalx.bootstrap.model;

import jakarta.validation.ValidatorFactory;
import net.microfalx.bootstrap.core.i18n.I18nService;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.microfalx.lang.AnnotationUtils.sort;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ClassUtils.isSubClassOf;

/**
 * A service which provides metadata about (data) models.
 */
@Service
public class MetadataService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataService.class);

    @Autowired(required = false) private Validator validator;
    @Autowired(required = false) private jakarta.validation.Validator jakartaValidator;
    @Autowired(required = false) private ValidatorFactory jakartaValidatorFactory;
    @Autowired(required = false) private ConversionService conversionService;
    @Autowired(required = false) private I18nService i18nService;
    @Autowired ApplicationContext applicationContext;

    private final List<MetadataProvider<?, ?, ?>> metadataProviders = new CopyOnWriteArrayList<>();
    private final List<MapperProvider> mapperProviders = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, Metadata<?, ? extends Field<?>, ?>> metadataCache = new ConcurrentHashMap<>();

    /**
     * Returns the metadata.
     *
     * @param model the model
     * @param <M>   the model type
     * @param <F>   the field type
     * @return a non-null instance
     */
    @SuppressWarnings("unchecked")
    public <M, F extends Field<M>, ID> Metadata<M, F, ID> getMetadata(Object model) {
        requireNonNull(model);
        return getMetadata((Class<M>) model.getClass());
    }

    /**
     * Returns the metadata.
     *
     * @param modelClass the model class
     * @param <M>        the model type
     * @param <F>        the field type
     * @return a non-null instance
     */
    @SuppressWarnings("unchecked")
    public <M, F extends Field<M>, ID> Metadata<M, F, ID> getMetadata(Class<M> modelClass) {
        requireNonNull(modelClass);
        Metadata<M, F, ID> metadata = (Metadata<M, F, ID>) metadataCache.get(modelClass);
        if (metadata == null) {
            MetadataProvider<M, Field<M>, ID> provider = find(modelClass);
            metadata = (Metadata<M, F, ID>) provider.getMetadata(modelClass);
            if (metadata instanceof AbstractMetadata<M, F, ID> ametadata) {
                ametadata.validator = validator;
                ametadata.metadataService = this;
                ametadata.initialize();
            }
            metadataCache.put(modelClass, metadata);
        }
        return metadata;
    }

    /**
     * Returns all registered providers.
     *
     * @return a non-null instance
     */
    public Collection<MetadataProvider<?, ?, ?>> getProviders() {
        return Collections.unmodifiableCollection(metadataProviders);
    }

    /**
     * Clears internal caches.
     */
    public void clear() {
        metadataCache.clear();
    }

    /**
     * Validates a model.
     *
     * @param model the model
     * @return the errors, empty if there are no errors
     */
    @SuppressWarnings("unchecked")
    public <M, F extends Field<M>, ID> Map<Field<M>, String> validate(M model) {
        requireNonNull(model);
        Metadata<M, F, ID> metadata = getMetadata(model);
        return (Map<Field<M>, String>) metadata.validate(model);
    }

    /**
     * Copies all the fields with the same name and type from the source model to the target model.
     *
     * @param source the source
     * @param target the target
     * @param <MS>   the model type of the source
     * @param <FS>   the field type of the source model
     * @param <IDS>  the identifier type of the source model
     * @param <MT>   the model type of the source
     * @param <FT>   the field type of the source model
     * @param <IDT>  the identifier type of the source model
     * @return the target
     */
    public <MS, FS extends Field<MS>, IDS, MT, FT extends Field<MT>, IDT> MT copy(MS source, MT target) {
        requireNonNull(source);
        requireNonNull(target);
        Metadata<MS, FS, IDS> sourceMetadata = getMetadata(source);
        Metadata<MT, FT, IDT> targetMetadata = getMetadata(target);
        for (FS sourceField : sourceMetadata.getFields()) {
            FT targetField = targetMetadata.find(sourceField.getName());
            if (targetField != null && isSubClassOf(targetField.getDataClass(), sourceField.getDataClass())) {
                targetField.set(target, sourceField.get(source));
            }
        }
        return target;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initialize();

    }

    protected I18nService getI18nService() {
        return i18nService;
    }

    protected void initialize() {
        initializeConvertorService();
        initializeValidator();
        initializeI18n();
        discoverMetadataProviders();
        Mapper.initialize(this);
        discoverMapperProviders();
        discoverMappers();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <M, F extends Field<M>, ID> MetadataProvider<M, F, ID> find(Class modelClass) {
        for (MetadataProvider<?, ?, ?> provider : metadataProviders) {
            if (provider.supports(modelClass)) return (MetadataProvider<M, F, ID>) provider;
        }
        throw new ModelException("A metadata provider is not registered for model " + ClassUtils.getName(modelClass));
    }

    private void initializeConvertorService() {
        if (conversionService == null) {
            LOGGER.warn("Convertor service is not available, use internal implementation");
            conversionService = new DefaultConversionService();
        } else {
            LOGGER.info("ConvertorService is available, implementation: {}",
                    ClassUtils.getName(conversionService));
        }
    }

    private void initializeValidator() {
        if (validator == null) {
            LOGGER.warn("Validator is not available, disable validation");
            validator = new NoOpValidator();
        } else {
            if (jakartaValidatorFactory != null && validator.getClass() != jakartaValidatorFactory.getClass()) {
                LOGGER.info("Validator is available, implementation: {}, validator: {}, factory: {}",
                        ClassUtils.getName(validator), ClassUtils.getName(jakartaValidator),
                        ClassUtils.getName(jakartaValidatorFactory));
            } else {
                LOGGER.info("Validator is available, implementation: {}", ClassUtils.getName(validator));
            }
            if (validator instanceof LocalValidatorFactoryBean lvfb) {
                jakarta.validation.Validator nativeValidator = lvfb.unwrap(jakarta.validation.Validator.class);
                jakarta.validation.ValidatorFactory nativeValidatorFactory = lvfb.unwrap(jakarta.validation.ValidatorFactory.class);
                LOGGER.info("Native validator implementation: {}, factory: {}",
                        ClassUtils.getName(nativeValidator), ClassUtils.getName(nativeValidatorFactory));
            }
        }
    }

    private void initializeI18n() {
        if (i18nService == null) {
            LOGGER.info("I18n service is not available, initializing default implementation");
            i18nService = new I18nService();
            try {
                i18nService.afterPropertiesSet();
            } catch (Exception e) {
                ExceptionUtils.rethrowException(e);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void discoverMetadataProviders() {
        LOGGER.debug("Discover metadata providers:");
        ServiceLoader<MetadataProvider> scannedProviders = ServiceLoader.load(MetadataProvider.class);
        for (MetadataProvider<?, ?, ?> scannedProvider : scannedProviders) {
            LOGGER.debug(" - {}", ClassUtils.getName(scannedProvider));
            metadataProviders.add(scannedProvider);
        }
        sort(metadataProviders);
        LOGGER.info("Discovered {} metadata providers", metadataProviders.size());
    }

    private void discoverMapperProviders() {
        LOGGER.debug("Discover mapper providers:");
        Collection<MapperProvider> scannedMapperProviders = ClassUtils.resolveProviderInstances(MapperProvider.class);
        for (MapperProvider scannedProvider : scannedMapperProviders) {
            LOGGER.debug(" - {}", ClassUtils.getName(scannedProvider));
            if (scannedProvider instanceof ApplicationContextAware aca) {
                aca.setApplicationContext(applicationContext);
            }
            this.mapperProviders.add(scannedProvider);
        }
        LOGGER.info("Discovered {} mapper providers", mapperProviders.size());
    }

    private void discoverMappers() {
        for (MapperProvider mapperProvider : mapperProviders) {
            mapperProvider.onInitializing();
        }
    }

    private static final class NoOpValidator implements Validator {

        @Override
        public boolean supports(Class<?> clazz) {
            return false;
        }

        @Override
        public void validate(@Nullable Object target, Errors errors) {
        }
    }
}

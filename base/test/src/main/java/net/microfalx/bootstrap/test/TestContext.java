package net.microfalx.bootstrap.test;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.bootstrap.application.ApplicationConfiguration;
import net.microfalx.bootstrap.application.ApplicationService;
import net.microfalx.bootstrap.core.config.BootstrapProperties;
import net.microfalx.bootstrap.core.config.ConverterConfiguration;
import net.microfalx.bootstrap.core.config.RetryConfiguration;
import net.microfalx.bootstrap.core.config.RetryProperties;
import net.microfalx.bootstrap.core.i18n.I18nProperties;
import net.microfalx.bootstrap.core.i18n.I18nService;
import net.microfalx.bootstrap.feature.FeatureService;
import net.microfalx.bootstrap.registry.RegistryConfiguration;
import net.microfalx.bootstrap.registry.RegistryService;
import net.microfalx.bootstrap.resource.ResourceService;
import net.microfalx.bootstrap.test.annotation.AnswerFor;
import net.microfalx.bootstrap.test.annotation.Prepare;
import net.microfalx.bootstrap.test.annotation.Subject;
import net.microfalx.bootstrap.test.answer.AbstractAnswer;
import net.microfalx.bootstrap.test.answer.JpaRepositoryAnswer;
import net.microfalx.bootstrap.test.extension.ComponentCreator;
import net.microfalx.lang.*;
import net.microfalx.registry.Registry;
import net.microfalx.resource.Resource;
import net.microfalx.resource.ResourceFactory;
import net.microfalx.threadpool.ThreadPool;
import org.atteo.classindex.ClassIndex;
import org.mockito.Mock;
import org.mockito.MockingDetails;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.validation.beanvalidation.OptionalValidatorFactoryBean;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.Collections.unmodifiableSet;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;
import static net.microfalx.lang.FileUtils.validateDirectoryExists;

/**
 * Holds instances for a test session
 */
@Slf4j
public class TestContext {

    private final Class<?> testClass;
    private final List<Field> testClassFields = new ArrayList<>();
    private Object testInstance;

    private AnnotationConfigApplicationContext applicationContext;
    private final Map<Class<?>, Class<?>> answersClasses = new HashMap<>();
    private final Set<Class<?>> subjectClasses = new LinkedHashSet<>();
    private Map<Class<?>, Object> objects = new HashMap<>();
    private final Set<Class<?>> mockClasses = new LinkedHashSet<>();
    private final List<ComponentCreator<?>> componentCreators = new ArrayList<>();
    private TaskTracker taskTracker = new TaskTracker();

    private File workingDirectory;

    static ThreadLocal<TestContext> CURRENT = new ThreadLocal<>();

    /**
     * Returns the session attached to the current thread.
     *
     * @return a non-null instance
     */
    public static Optional<TestContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public TestContext(Class<?> testClass) {
        requireNonNull(testClass);
        this.testClass = testClass;
    }

    /**
     * Invoked during the setup of the test class, to initialize metadata.
     */
    public void beforeAll() {
        CURRENT.set(this);
        initSettings();
        initFields();
        initComponentCreators();
        discoverAnswersClasses();
        scanPrepareAnnotations();
        scanTestClasses();
    }

    /**
     * Invoked during the teardown of the test class, to cleanup.
     */
    public void afterAll() {
        CURRENT.remove();
    }

    /**
     * Invoked before a test method is called, to setup the test class.
     *
     * @param testInstance the instance of the test class
     */
    public void beforeEach(Object testInstance) {
        this.testInstance = testInstance;
        initDirectory();
        initContext();
        initCoreServices();
        injectTestInstances();
    }

    /**
     * Invoked after a test method is called, to destroy and cleanup the test class.
     */
    public void afterEach() {
        // nothing to cleanup for now
    }

    /**
     * Returns the working directory associated with the tests.
     * <p>
     * Each test (method) receives its own working directory
     *
     * @return a non-null instance
     */
    public File getWorkingDirectory() {
        if (workingDirectory == null) throw new IllegalStateException("Working directory not set");
        return workingDirectory;
    }

    /**
     * Returns the Spring application context for the current test.
     *
     * @return a non-null instance
     */
    public ApplicationContext getApplicationContext() {
        if (applicationContext == null) throw new IllegalStateException("Application context not set");
        return applicationContext;
    }

    /**
     * Returns the subjects (real objects, directly and indirect under test).
     *
     * @return a non-null instance
     */
    public Set<Class<?>> getSubjectClasses() {
        return unmodifiableSet(subjectClasses);
    }

    /**
     * Returns the mocks (directly and indirect under test).
     *
     * @return a non-null instance
     */
    public Set<Class<?>> getMockClasses() {
        return unmodifiableSet(mockClasses);
    }

    /**
     * Looks up an instance by its type.
     *
     * @param type the class of the object
     * @param <T>  the type of object
     * @return the instance, null if not defined
     */
    @SuppressWarnings("unchecked")
    public <T> T lookup(Class<T> type) {
        requireNonNull(type);
        return (T) objects.get(type);
    }

    /**
     * Resolves up an instance by its type.
     *
     * @param type the class of the object
     * @param <T>  the type of object
     * @return a non-null instance
     * @throws IllegalArgumentException if such a type is not registered
     */
    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<T> type) {
        requireNonNull(type);
        T instance = (T) objects.get(type);
        if (instance == null) {
            throw new IllegalArgumentException("An object with type '" + ClassUtils.getName(type) + "' is not available");
        }
        return instance;
    }

    /**
     * Returns the tracker for concurrency.
     *
     * @return a non-null instance
     */
    public TaskTracker getTaskTracker() {
        return taskTracker;
    }

    private void initCoreServices() {
        applicationContext = new AnnotationConfigApplicationContext();
        registerCoreProperties();
        registerCoreConfigurations();
        registerCoreServices();
        registerCoreMocksWithContext();
        registerPreparedWithContext();
        if (!getSubjectClasses().isEmpty()) {
            applicationContext.register(getSubjectClasses().toArray(new Class[0]));
        }
        applicationContext.refresh();
        updateObjectsFromApplicationContext();
    }

    private void registerCoreProperties() {
        applicationContext.register(BootstrapProperties.class);
        applicationContext.register(RetryProperties.class);
        applicationContext.register(I18nProperties.class);
        applicationContext.register(RetryConfiguration.class);
        applicationContext.register(ConverterConfiguration.class);
        applicationContext.register(RegistryConfiguration.class);
    }

    private void registerCoreConfigurations() {
        applicationContext.register(RetryConfiguration.class);
        applicationContext.register(ConverterConfiguration.class);
        applicationContext.register(RegistryConfiguration.class);
        applicationContext.register(ApplicationConfiguration.class);
    }

    private void registerCoreServices() {
        applicationContext.register(I18nService.class);
        applicationContext.register(ApplicationService.class);
        applicationContext.register(RegistryService.class);
        applicationContext.register(ResourceService.class);
        applicationContext.register(FeatureService.class);
        applicationContext.register(OptionalValidatorFactoryBean.class);
    }

    private <T> void registerCoreMocksWithContext() {
        registerMockWithContext(ScheduledExecutorService.class);
    }

    private <T> void registerPreparedWithContext() {
        for (Class<?> mockClass : mockClasses) {
            registerMockWithContext(mockClass);
            if (mockClass == RegistryService.class) registerMockWithContext(Registry.class);
        }
    }

    private <T> void registerMockWithContext(Class<T> type) {
        applicationContext.registerBean(type, () -> mock(type));
    }

    private void createComponent(Class<?> componentClass) {
        applicationContext.register(componentClass);
        applicationContext.refresh();
        registerObject(componentClass, applicationContext.getBean(componentClass));
    }

    private void initializeComponent(Object component) {
        if (component instanceof InitializingBean initializingBean) {
            try {
                initializingBean.afterPropertiesSet();
            } catch (Exception e) {
                ExceptionUtils.rethrowException(e);
            }
        }
    }

    private void registerObject(Class<?> type, Object instance) {
        initializeComponent(instance);
        objects.put(type, instance);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T mock(Class<T> type) {
        T instance = (T) objects.get(type);
        if (instance != null) return instance;
        Class<?> answerClass = answersClasses.get(type);
        if (answerClass != null) {
            try {
                Answer<?> answer = (Answer<?>) ClassUtils.create(answerClass);
                if (answer instanceof AbstractAnswer abstractAnswer) {
                    abstractAnswer.initialize(this);
                }
                instance = Mockito.mock(type, answer);
            } catch (Exception e) {
                return rethrowExceptionAndReturn(e);
            }
        } else if (TestUtils.isJpaAvailable() && JpaRepositoryAnswer.isRepository(type)) {
            instance = (T) JpaRepositoryAnswer.mock(type);
        } else {
            instance = Mockito.mock(type, Mockito.RETURNS_SMART_NULLS);
        }
        registerObject(type, instance);
        return instance;
    }

    private void initContext() {
        this.applicationContext = new AnnotationConfigApplicationContext();
        this.objects = new HashMap<>();
        this.taskTracker = new TaskTracker();
        this.objects.put(TestContext.class, this);
        this.objects.put(TaskTracker.class, taskTracker);
    }

    private void initSettings() {
        // nothing right now
    }

    private void initDirectory() {
        workingDirectory = new File(JvmUtils.getWorkingDirectory(), "target");
        if (!workingDirectory.exists()) {
            workingDirectory = JvmUtils.getTemporaryDirectory();
        } else {
            ResourceFactory.setTemporary(Resource.directory(workingDirectory));
        }
        workingDirectory = validateDirectoryExists(new File(workingDirectory, IdGenerator.get().nextAsString()));
        LOGGER.debug("Working director for '{}' is '{}'", ClassUtils.getName(testClass), workingDirectory);
        JvmUtils.setCacheDirectory(workingDirectory);

    }

    private void initComponentCreators() {
        ClassIndex.getSubclasses(ComponentCreator.class).forEach(cc -> {
            componentCreators.add(ClassUtils.create(cc));
        });
        AnnotationUtils.sort(componentCreators);
        LOGGER.debug("Loaded {} component creators", componentCreators.size());
    }

    private void discoverAnswersClasses() {
        Set<Class<?>> registeredTypes = new HashSet<>();
        for (Class<?> answerClass : ClassIndex.getAnnotated(AnswerFor.class)) {
            AnswerFor answerForAnnot = answerClass.getAnnotation(AnswerFor.class);
            Class<?>[] componentTypes = answerForAnnot.value();
            for (Class<?> componentType : componentTypes) {
                if (!registeredTypes.add(componentType)) {
                    throw new IllegalStateException("Multiple answers (last " + ClassUtils.getName(answerClass)
                            + ") for component type '" + componentType + "' found");
                }
                answersClasses.put(componentType, answerClass);
            }
        }
        LOGGER.debug("Loaded {} answers", answersClasses.size());
    }

    private void scanPrepareAnnotations() {
        mockClasses.addAll(Arrays.asList(Executor.class, ExecutorService.class, ScheduledExecutorService.class,
                ThreadPool.class));
        Collection<Prepare> prepareAnnotations = AnnotationUtils.getAnnotations(testClass, Prepare.class);
        for (Prepare prepareAnnotation : prepareAnnotations) {
            mockClasses.addAll(Arrays.asList(prepareAnnotation.mocks()));
            subjectClasses.addAll(Arrays.asList(prepareAnnotation.subjects()));
        }
    }

    private void scanTestClasses() {
        for (Field field : testClassFields) {
            if (field.isAnnotationPresent(Mock.class)) {
                mockClasses.add(field.getType());
            }
            if (field.isAnnotationPresent(Subject.class)) {
                subjectClasses.add(field.getType());
            }
        }
    }

    private void initFields() {
        this.testClassFields.addAll(ReflectionUtils.openFields(testClass));
    }

    private void updateObjectsFromApplicationContext() {
        for (Class<?> type : subjectClasses) {
            Object bean = applicationContext.getBean(type);
            objects.put(type, bean);
        }
    }

    private void injectTestInstances() {
        for (Field field : testClassFields) {
            try {
                Object currentFieldValue = field.get(testInstance);
                Class<?> fieldType = field.getType();
                Object preparedFieldValue = objects.get(fieldType);
                boolean shouldSet = currentFieldValue == null || preparedFieldValue != null;
                if (currentFieldValue != null) {
                    // if already a mock, and we have a new mock, replace
                    MockingDetails mockingDetails = Mockito.mockingDetails(currentFieldValue);
                    shouldSet = mockingDetails.isMock();
                }
                if (preparedFieldValue != null && shouldSet) {
                    field.set(testInstance, preparedFieldValue);
                }
            } catch (Exception e) {
                ExceptionUtils.rethrowException(e);
            }
        }
    }


}

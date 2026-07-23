package net.microfalx.bootstrap.template;

import net.microfalx.metrics.Metrics;
import net.microfalx.resource.Resource;

import java.io.IOException;
import java.io.OutputStream;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;

/**
 * Base class for all templates
 */
public abstract class AbstractTemplate implements Template, Cloneable {

    private static final Metrics EXPRESSION = TemplateUtils.METRICS.withGroup("Evaluate Expression");
    private static final Metrics RESOURCE = TemplateUtils.METRICS.withGroup("Evaluate Resource");

    private final TemplateService templateService;
    private final Resource resource;
    private Mode mode = Mode.HTML;

    public AbstractTemplate(TemplateService templateService, Resource resource) {
        requireNonNull(templateService);
        requireNonNull(resource);
        this.templateService = templateService;
        this.resource = resource;
    }

    @Override
    public final Mode getMode() {
        return mode;
    }

    @Override
    public final Resource getResource() {
        return resource;
    }

    public final TemplateService getTemplateService() {
        return templateService;
    }

    @Override
    public final <T> T evaluate(TemplateContext context) {
        try {
            return EXPRESSION.timeCallable(resource.getName(), () -> doEvaluate(context));
        } catch (Exception e) {
            throw new TemplateException("Failed to evaluate expression '" + getResource() + "'", e);
        }
    }

    @Override
    public final void evaluate(TemplateContext context, OutputStream outputStream) throws IOException {
        try {
            RESOURCE.timeCallable(resource.getPath(), () -> {
                doEvaluate(context, outputStream);
                return null;
            });
        } catch (Exception e) {
            throw new TemplateException("Failed to evaluate expression '" + getResource() + "'", e);
        }
    }

    @Override
    public final Template withMode(Mode mode) {
        AbstractTemplate copy = copy();
        copy.mode = mode;
        return copy;
    }

    /**
     * Subclasses would implement this method to evaluate expressions.
     *
     * @param context the context
     * @param <T>     the return type
     * @return the return value
     * @throws Exception if the expression cannot be evaluated
     */
    protected abstract <T> T doEvaluate(TemplateContext context) throws Exception;

    /**
     * Subclasses would implement this method to evaluate a template and produce an output.
     *
     * @param context      the context
     * @param outputStream the output stream where to write the output     *
     * @throws Exception if the expression cannot be evaluated
     */
    protected abstract void doEvaluate(TemplateContext context, OutputStream outputStream) throws Exception;

    protected AbstractTemplate copy() {
        try {
            return (AbstractTemplate) clone();
        } catch (CloneNotSupportedException e) {
            return rethrowExceptionAndReturn(e);
        }
    }
}

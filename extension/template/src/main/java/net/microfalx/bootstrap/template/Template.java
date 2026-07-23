package net.microfalx.bootstrap.template;

import net.microfalx.resource.Resource;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A compiled template (expression) which can be evaluated multiple types.
 */
public interface Template {

    /**
     * Returns the template type.
     *
     * @return a non-null enum
     */
    Type getType();

    /**
     * Returns the operating mode of the template.
     *
     * @return a non-null instance
     */
    Mode getMode();

    /**
     * Returns the resource of the template body (expression).
     *
     * @return a non-null instance
     */
    Resource getResource();

    /**
     * Evaluates the template (expression) and returns the result.
     *
     * @param context the evaluation context
     * @param <T>     the return type
     * @return the result
     */
    <T> T evaluate(TemplateContext context);

    /**
     * Evaluates the template and write the result to a writer.
     *
     * @param context      the evaluation
     * @param outputStream the output stream
     */
    void evaluate(TemplateContext context, OutputStream outputStream) throws IOException;

    /**
     * Changes the operating mode of the template.
     *
     * @param mode the new mode
     * @return a new template instance with the specified mode
     */
    Template withMode(Mode mode);

    /**
     * An enum which identifies the rendering mode of the template engine
     */
    enum Mode {

        /**
         * The input is in plain text format.
         */
        TEXT,

        /**
         * The input is HTML format.
         */
        HTML,

        /**
         * The input is in XML format.
         */
        XML,

        /**
         * The input is in CSS format.
         */
        CSS,

        /**
         * The input is in JavaScript format.
         */
        JAVASCRIPT,

        /**
         * The input is
         */
        RAW
    }

    /**
     * An enum which identifies the template engine.
     */
    enum Type {

        /**
         * An expression evaluator <a href="http://mvel.documentnode.com/">MVEL</a>
         */
        MVEL,

        /**
         * An expression evaluator <a href="https://www.thymeleaf.org/">Thymeleaf</a>
         */
        THYMELEAF

    }
}

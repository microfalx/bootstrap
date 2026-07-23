package net.microfalx.bootstrap.template;

import net.microfalx.lang.EnumUtils;
import net.microfalx.resource.Resource;
import org.apache.commons.io.output.WriterOutputStream;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.templatemode.TemplateMode;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

final class ThymeleafTemplate extends AbstractTemplate {

    ThymeleafTemplate(TemplateService templateService, Resource resource) {
        super(templateService, resource);
    }

    @Override
    public Type getType() {
        return Type.THYMELEAF;
    }

    @Override
    public <T> T doEvaluate(TemplateContext context) throws Exception {
        StringWriter writer = new StringWriter();
        WriterOutputStream outputStream = new WriterOutputStream(writer, StandardCharsets.UTF_8);
        doEvaluate(context, outputStream);
        return (T) writer.toString();
    }

    @Override
    public void doEvaluate(TemplateContext context, OutputStream outputStream) throws Exception {
        TemplateEngine templateEngine = getTemplateService().getTemplateEngine();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
        Map<String, Object> variables = context instanceof AbstractTemplateContext ? ((AbstractTemplateContext<?, ?, ?>) context).getVariables() : context.toMap();
        IContext thymeleafContext = new Context(Locale.getDefault(), variables);
        TemplateSpec templateSpec = new TemplateSpec(getResource().loadAsString(), getTemplateMode());
        templateEngine.process(templateSpec, thymeleafContext, outputStreamWriter);
    }

    public TemplateMode getTemplateMode() {
        return EnumUtils.fromName(TemplateMode.class, getMode().name(), TemplateMode.RAW);
    }

}

package net.microfalx.bootstrap.template;

import net.microfalx.bootstrap.model.Attribute;
import net.microfalx.bootstrap.model.Attributes;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThymeleafTemplateTest extends AbstractTemplateTest {

    private static final String TEMPLATE_MODEL = "<html><body><div th:text='${model.firstName}'></body></html>";
    private static final String TEMPLATE_PERSON = "<html><body><div th:text='${person.firstName}'></body></html>";
    private static final String TEMPLATE_TEXT = "Hello [(${model.firstName})]";

    @Test
    public void evaluateWithModel() {
        Template template = templateService.getTemplate(Template.Type.THYMELEAF, TEMPLATE_MODEL);
        TemplateContext templateContext = templateService.createContext(new Person());
        assertEquals("<html><body><div>John</body></html>", template.evaluate(templateContext));
    }

    @Test
    public void evaluateWithModelAndClassPath() {
        Template template = templateService.loadTemplate(Template.Type.THYMELEAF, "test.html");
        TemplateContext templateContext = templateService.createContext(new Person());
        assertEquals("<html><body><div>John</body></html>", template.evaluate(templateContext));
    }

    @Test
    public void evaluateWithAttributes() {
        Template template = templateService.getTemplate(Template.Type.THYMELEAF, TEMPLATE_PERSON);
        Attributes<Attribute> attributes = Attributes.create();
        TemplateContext templateContext = templateService.createContext(attributes);
        attributes.add("person", new Person());
        assertEquals("<html><body><div>John</body></html>", template.evaluate(templateContext));
    }

    @Test
    public void evaluateWithText() {
        Template template = templateService.getTemplate(Template.Type.THYMELEAF, TEMPLATE_TEXT)
                .withMode(Template.Mode.TEXT);
        TemplateContext templateContext = templateService.createContext(new Person());
        assertEquals("Hello John", template.evaluate(templateContext));
    }


    @Test
    public void evaluateTemplate() throws IOException {
        Template template = templateService.getTemplate(Template.Type.THYMELEAF, TEMPLATE_PERSON);
        TemplateContext templateContext = templateService.createContext();
        templateContext.set("person", new Person());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        template.evaluate(templateContext, buffer);
        assertEquals("<html><body><div>John</body></html>", buffer.toString());
    }

    @Test
    public void evaluateNoCache() throws Exception {
        templateProperties.setCached(false);
        templateService.afterPropertiesSet();
        evaluateWithModel();
    }

}
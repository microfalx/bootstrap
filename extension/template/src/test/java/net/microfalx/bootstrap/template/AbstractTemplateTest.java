package net.microfalx.bootstrap.template;

import net.microfalx.bootstrap.core.i18n.I18nService;
import net.microfalx.bootstrap.model.MetadataService;
import org.joor.Reflect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractTemplateTest {

    @InjectMocks
    protected MetadataService metadataService;

    @InjectMocks
    protected I18nService i18nService;

    @InjectMocks
    protected TemplateService templateService;

    @InjectMocks
    protected TemplateProperties templateProperties;

    @BeforeEach
    public void setup() throws Exception {
        metadataService.afterPropertiesSet();
        Reflect.on(templateService).set("metadataService", metadataService);
        Reflect.on(metadataService).set("i18nService", i18nService);
        Reflect.on(templateService).set("properties", templateProperties);
        templateService.afterPropertiesSet();
        metadataService.afterPropertiesSet();
        templateService.afterPropertiesSet();
    }
}

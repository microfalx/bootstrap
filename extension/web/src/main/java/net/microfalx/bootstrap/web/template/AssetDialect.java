package net.microfalx.bootstrap.web.template;

import net.microfalx.bootstrap.web.application.ApplicationException;
import net.microfalx.bootstrap.web.application.ApplicationService;
import net.microfalx.bootstrap.web.application.Asset;
import net.microfalx.bootstrap.web.application.AssetBundle;
import net.microfalx.lang.StringUtils;
import net.microfalx.resource.Resource;
import org.springframework.context.ApplicationContext;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static net.microfalx.lang.StringUtils.EMPTY_STRING;
import static net.microfalx.lang.TextUtils.MEDIUM_INDENT;
import static net.microfalx.lang.TextUtils.insertSpaces;

/**
 * Processors for assets.
 */
public class AssetDialect extends AbstractProcessorDialect {

    private static final String DIALECT_PREFIX = "asset";
    private static final String DIALECT_NAME = "Bootstrap Asset";
    private static final int PRECEDENCE = 1000;

    private ApplicationService applicationService;

    public AssetDialect(ApplicationContext applicationContext) {
        super(DIALECT_NAME, DIALECT_PREFIX, PRECEDENCE, applicationContext);
        this.applicationService = applicationContext.getBean(ApplicationService.class);
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new HashSet<>();
        processors.add(new ScriptsAssetTagProcessor());
        processors.add(new StylesheetsAssetTagProcessor());
        return processors;
    }

    private abstract class BaseAssetTagProcessor extends AbstractElementTagProcessor {

        public BaseAssetTagProcessor(String elementName) {
            super(TemplateMode.HTML, DIALECT_PREFIX, elementName, true, null, false, PRECEDENCE);
        }

        @Override
        protected void doProcess(ITemplateContext context, IProcessableElementTag tag, IElementTagStructureHandler structureHandler) {
            String[] bundles = getBundles(tag);
            if (areBundlesEmpty(bundles)) {
                structureHandler.replaceWith("", false);
            } else {
                if (isInline(tag)) {
                    writeInline(getType(), bundles, tag, structureHandler);
                } else {
                    writeTags(getType(), bundles, tag, structureHandler);
                }
            }
        }

        protected abstract Asset.Type getType();

        protected boolean isInline(IProcessableElementTag tag) {
            IAttribute inline = tag.getAttribute("inline");
            return inline != null && StringUtils.asBoolean(inline.getValue(), false);
        }

        protected String[] getBundles(IProcessableElementTag tag) {
            IAttribute bundles = tag.getAttribute("bundles");
            if (bundles == null) return StringUtils.EMPTY_STRING_ARRAY;
            return StringUtils.split(bundles.getValue(), ",");
        }

        protected void writeTag(StringBuilder builder, boolean start, int indent) {
            builder.append(StringUtils.getStringOfChar(' ', indent))
                    .append("<").append(start ? EMPTY_STRING : "/");
            switch (getType()) {
                case STYLE_SHEET -> builder.append("style");
                case JAVA_SCRIPT -> builder.append("script");
            }
            builder.append(">\n");
        }

        protected void writeInline(Asset.Type type, String[] bundles, IProcessableElementTag tag, IElementTagStructureHandler structureHandler) {
            StringBuilder builder = new StringBuilder();
            try {
                Resource content = applicationService.getAssetBundlesContent(type, false, bundles);
                writeTag(builder, true, tag.getCol());
                builder.append(insertSpaces(content.loadAsString(), tag.getCol() + MEDIUM_INDENT, false, false, true))
                        .append('\n');
                writeTag(builder, false, tag.getCol());
                structureHandler.replaceWith(builder.toString(), false);
            } catch (IOException e) {
                throw new ApplicationException("Failed to assemble content for asset bundles " + Arrays.toString(bundles), e);
            }
        }

        protected void writeTags(Asset.Type type, String[] bundles, IProcessableElementTag tag, IElementTagStructureHandler structureHandler) {
            Collection<AssetBundle> assetBundles = applicationService.getAssetBundles(bundles);
            String tags = applicationService.getAssetBundleTags(type, tag.getCol() - 1, assetBundles);
            structureHandler.replaceWith(tags, false);
        }

        private boolean areBundlesEmpty(String[] bundles) {
            if (bundles.length == 0) return false;
            for (String bundle : bundles) {
                if (!applicationService.getAssetBundle(bundle).getAssets().isEmpty()) return false;
            }
            return true;
        }
    }

    private class ScriptsAssetTagProcessor extends BaseAssetTagProcessor {

        public ScriptsAssetTagProcessor() {
            super("js");
        }

        @Override
        protected Asset.Type getType() {
            return Asset.Type.JAVA_SCRIPT;
        }
    }

    private class StylesheetsAssetTagProcessor extends BaseAssetTagProcessor {

        public StylesheetsAssetTagProcessor() {
            super("css");
        }

        @Override
        protected Asset.Type getType() {
            return Asset.Type.STYLE_SHEET;
        }

    }
}

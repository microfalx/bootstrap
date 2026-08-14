package net.microfalx.bootstrap.web.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.ToString;
import net.microfalx.bootstrap.dataset.DataSetService;
import net.microfalx.bootstrap.search.SearchUtils;
import net.microfalx.bootstrap.security.userinfo.ExtendedUserDetails;
import net.microfalx.bootstrap.web.application.ApplicationService;
import net.microfalx.bootstrap.web.application.LocalStorage;
import net.microfalx.bootstrap.web.application.Theme;
import net.microfalx.bootstrap.web.chart.ChartService;
import net.microfalx.bootstrap.web.container.WebContainerRequest;
import net.microfalx.bootstrap.web.template.tools.DataSetTool;
import net.microfalx.bootstrap.web.template.tools.LinkTool;
import net.microfalx.bootstrap.web.util.Gravatar;
import net.microfalx.lang.IdGenerator;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.ObjectUtils;
import net.microfalx.lang.TextUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Character.MAX_RADIX;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;
import static net.microfalx.lang.StringUtils.*;
import static org.apache.commons.lang3.StringEscapeUtils.escapeEcmaScript;

/**
 * Processors for {@link net.microfalx.bootstrap.web.application.Application}.
 */
public class ApplicationDialect extends AbstractProcessorDialect {

    private static final String DIALECT_PREFIX = "application";
    private static final String DIALECT_NAME = "Bootstrap Application";
    private static final int PRECEDENCE = 1000;

    private static final IdGenerator APP_ID_GENERATOR = IdGenerator.get("web.application");

    private final ApplicationService applicationService;
    private final DataSetService dataSetService;
    private final ChartService chartService;

    public ApplicationDialect(ApplicationContext applicationContext) {
        super(DIALECT_NAME, DIALECT_PREFIX, PRECEDENCE, applicationContext);
        this.applicationService = applicationContext.getBean(ApplicationService.class);
        this.dataSetService = applicationContext.getBean(DataSetService.class);
        this.chartService = applicationContext.getBean(ChartService.class);
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new HashSet<>();
        processors.add(new ContextTagProcessor());
        processors.add(new ThemeModeAttributeProcessor());
        return processors;
    }

    private static UserInfo getCurrentUser() {
        UserInfo userInfo = new UserInfo();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        userInfo.id = "anonymous";
        userInfo.userName = "anonymous";
        userInfo.name = "Anonymous";
        userInfo.authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Identifiable<?> identifiable) {
                Object id = identifiable.getId();
                if (id instanceof Number idAsNumber) id = Long.toString(idAsNumber.longValue(), MAX_RADIX);
                userInfo.id = ObjectUtils.toString(id);
            }
            if (principal instanceof UserDetails userDetails) {
                userInfo.userName = userDetails.getUsername();
                userInfo.name = authentication.getName();
                userInfo.roles.addAll(authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
            }
            if (principal instanceof ExtendedUserDetails userDetails) {
                userInfo.email = userDetails.getEmail();
                userInfo.external = userDetails.isExternal();
                userInfo.resetPassword = userDetails.isResetPassword();
                userInfo.imageUrl = userDetails.getImageUrl();
                if (isEmpty(userInfo.imageUrl) && isNotEmpty(userInfo.email)) {
                    userInfo.imageUrl = new Gravatar(userInfo.email).getUrl();
                }
            }
        }
        return userInfo;
    }

    private static String getCurrentUserAsJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(getCurrentUser());
        } catch (JsonProcessingException e) {
            return rethrowExceptionAndReturn(e);
        }
    }

    private abstract class BaseAttributeProcessor extends AbstractAttributeTagProcessor {

        public BaseAttributeProcessor(String attributeName) {
            super(TemplateMode.HTML, DIALECT_PREFIX, null, true, attributeName, true, PRECEDENCE, true);
        }

    }

    private abstract class BaseTagProcessor extends AbstractElementTagProcessor {

        public BaseTagProcessor(String elementName) {
            super(TemplateMode.HTML, DIALECT_PREFIX, elementName, true, null, false, PRECEDENCE);
        }

    }

    private class ThemeModeAttributeProcessor extends BaseAttributeProcessor {

        public ThemeModeAttributeProcessor() {
            super("theme-mode");
        }

        @Override
        protected void doProcess(ITemplateContext context, IProcessableElementTag tag, AttributeName attributeName, String attributeValue, IElementTagStructureHandler structureHandler) {
            ApplicationService applicationService = applicationContext.getBean(ApplicationService.class);
            Theme theme = applicationService.getCurrentTheme();
            Theme.Mode mode = theme.getMode();
            if (mode != Theme.Mode.AUTO) {
                structureHandler.setAttribute("data-bs-theme", mode.name().toLowerCase());
            }
        }
    }

    private class ContextTagProcessor extends BaseTagProcessor {

        public ContextTagProcessor() {
            super("context");
        }

        @Override
        protected void doProcess(ITemplateContext context, IProcessableElementTag tag, IElementTagStructureHandler structureHandler) {
            WebContainerRequest containerRequest = WebContainerRequest.get();
            boolean authenticated = getCurrentUser().isAuthenticated();
            CsrfToken csrf = (CsrfToken) containerRequest.getRequest().getAttribute("_csrf");
            LinkTool linkTool = new LinkTool(context, applicationContext);
            DataSetTool<?, ?, ?> dataSetTool = new DataSetTool<>(context, applicationContext);
            StringBuilder builder = new StringBuilder();
            builder.append(SCRIPT_START_TAG);
            builder.append("\nconst APP_ID=\"").append(APP_ID_GENERATOR.nextAsString()).append("\";");
            builder.append("\nconst APP_AUTHENTICATED=").append(authenticated).append(";");
            builder.append("\nconst APP_REQUEST_URL=\"").append(linkTool.getUrl()).append("\";");
            builder.append("\nconst APP_REQUEST_PATH=\"").append(linkTool.getSelf()).append("\";");
            builder.append("\nconst APP_REQUEST_QUERY=").append(linkTool.toJson(linkTool.getQuery())).append(";");
            if (csrf != null) {
                csrf = new DefaultCsrfToken(csrf.getHeaderName(), csrf.getParameterName(), csrf.getToken());
                builder.append("\nconst APP_CSRF=").append(linkTool.toJson(csrf)).append(";");
            } else {
                builder.append("\nconst APP_CSRF=false").append(";");
            }
            String timeZone = containerRequest.hasTimeZone() ? containerRequest.getTimeZone().getId() : EMPTY_STRING;
            builder.append("\nconst APP_TIME_ZONE=\"").append(timeZone).append("\";");
            builder.append("\nconst APP_USER=").append(getCurrentUserAsJson()).append(";");
            Theme theme = applicationService.getCurrentTheme();
            builder.append("\nconst APP_THEME=\"").append(theme.getId()).append("\";");
            builder.append("\nconst APP_THEME_MODE=\"").append(theme.getMode().name().toLowerCase()).append("\";");
            String localStorageJson = LocalStorage.isEmpty() ? "null" : linkTool.toJson(LocalStorage.get().toMap());
            builder.append("\nconst APP_LOCAL_STORAGE=").append(localStorageJson).append(";");
            String filterableOperator = defaultIfEmpty(dataSetTool.getFilterableOperator(), SearchUtils.DEFAULT_FILTER_OPERATOR);
            builder.append("\nconst DATASET_FILTERABLE_OPERATOR=\"").append(filterableOperator).append("\";");
            String filterableQuoteChar = defaultIfEmpty(dataSetTool.getFilterableQuoteChar(), String.valueOf(SearchUtils.DEFAULT_FILTER_QUOTE_CHAR));
            builder.append("\nconst DATASET_FILTERABLE_QUOTE_CHAR=\"").append(escapeEcmaScript(filterableQuoteChar)).append("\";");
            builder.append("\nconst SEARCH_ENGINE_FILTERABLE_OPERATOR=\"").append(SearchUtils.DEFAULT_FILTER_OPERATOR).append("\";");
            builder.append("\nconst SEARCH_ENGINE_FILTERABLE_QUOTE_CHAR=\"").append(escapeEcmaScript(String.valueOf(SearchUtils.DEFAULT_FILTER_QUOTE_CHAR))).append("\";");
            builder.append('\n').append(SCRIPT_START_END);
            structureHandler.replaceWith(TextUtils.insertSpaces(builder.toString(), tag.getCol()), false);

        }
    }

    private static final String SCRIPT_START_TAG = "<script type=\"text/javascript\">";
    private static final String SCRIPT_START_END = "</script>";

    @Getter
    @ToString
    private static final class UserInfo {

        private String id;
        private String userName;
        private String name;
        private String email;
        private boolean authenticated;
        private boolean external;
        private boolean resetPassword;
        private String imageUrl;
        private final Set<String> roles = new HashSet<>();
    }
}

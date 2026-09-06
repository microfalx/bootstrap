package net.microfalx.bootstrap.security.user;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import net.microfalx.argus.api.Issue;
import net.microfalx.bootstrap.core.utils.ApplicationContextSupport;
import net.microfalx.bootstrap.core.utils.Json;
import net.microfalx.bootstrap.restapi.ApiCredentialService;
import net.microfalx.bootstrap.security.SecurityConstants;
import net.microfalx.bootstrap.security.SecurityContext;
import net.microfalx.bootstrap.security.SecurityUtils;
import net.microfalx.bootstrap.security.audit.AuditContext;
import net.microfalx.bootstrap.security.audit.jpa.Audit;
import net.microfalx.bootstrap.security.audit.jpa.AuditRepository;
import net.microfalx.bootstrap.security.group.GroupService;
import net.microfalx.bootstrap.security.group.jpa.Group;
import net.microfalx.bootstrap.security.provisioning.SecurityProperties;
import net.microfalx.bootstrap.security.user.jpa.User;
import net.microfalx.bootstrap.security.user.jpa.UserRepository;
import net.microfalx.bootstrap.security.user.jpa.UserSetting;
import net.microfalx.bootstrap.security.user.jpa.UserSettingRepository;
import net.microfalx.bootstrap.security.userinfo.ExtendedUserDetails;
import net.microfalx.bootstrap.web.preference.PreferenceService;
import net.microfalx.bootstrap.web.preference.PreferenceStorage;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.microfalx.bootstrap.security.SecurityConstants.ADMINISTRATORS_GROUP;
import static net.microfalx.bootstrap.security.SecurityConstants.USERS_GROUP;
import static net.microfalx.bootstrap.security.SecurityUtils.*;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.StringUtils.*;

/**
 * A service around user management.
 */
@Service
public class UserService extends ApplicationContextSupport implements ApiCredentialService, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private static final int DEFAULT_USER_COUNT = 2;
    private static final String LOGIN_ACTION = "Login";
    private static final String LOGOUT_ACTION = "Logout";
    private static final String SECURITY_MODULE = "Security";

    @Autowired private UserRepository userRepository;
    @Autowired private UserSettingRepository userSettingRepository;
    @Autowired private AuditRepository auditRepository;

    @Autowired private SecurityProperties settings;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private GroupService groupService;
    @Autowired private UserDetailsManager userDetailsManager;
    @Autowired(required = false) private OAuth2AuthorizedClientService authorizedClientService;

    @Autowired private JdbcClient jdbcClient;

    private final Map<String, Role> roles = new ConcurrentHashMap<>();
    private final Map<String, SecurityContext> securityContexts = new ConcurrentHashMap<>();
    private final LoadingCache<String, UserDetails> tokenCache = CacheBuilder.newBuilder()
            .maximumSize(100).expireAfterWrite(Duration.ofSeconds(30))
            .build(new TokenCacheLoader());

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public PasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    /**
     * Returns the roles registered with the application.
     *
     * @return a non-null instance
     */
    public Set<Role> getRoles() {
        return Set.copyOf(roles.values());
    }

    /**
     * Returns a role by its identifier.
     *
     * @param id the role identifier
     * @return the role
     * @throws SecurityException if the role does not exist
     */
    public Role getRole(String id) {
        requireNotEmpty(id);
        Role role = roles.get(toIdentifier(id));
        if (role == null) throw new SecurityException(" A role with identifier '" + id + " is not registered");
        return role;
    }

    /**
     * Registers a new role.
     *
     * @param role the role
     */
    public void registerRole(Role role) {
        requireNonNull(role);
        LOGGER.info("Register role '{}', name '{}", role.getId(), role.getName());
        this.roles.put(StringUtils.toIdentifier(role.getId()), role);
    }

    @Override
    public UserDetails authenticateBearer(String token) {
        requireNotEmpty(token);
        return tokenCache.getUnchecked(token);
    }

    @Override
    public UserDetails authenticateApiKey(String apiKey) {
        requireNotEmpty(apiKey);
        return tokenCache.getUnchecked(apiKey);
    }

    /**
     * Checks whether a user with a given username exists.
     *
     * @param userName the username
     * @return {@code true} if such a user exists, {@code false} otherwise
     */
    public boolean exists(String userName) {
        requireNonNull(userName);
        return findUser(false, userName) != null;
    }

    /**
     * Finds a user by its username.
     *
     * @param userName the user name
     * @return the user, null if such a user does not exist
     */
    public User findUser(String userName) {
        requireNonNull(userName);
        return findUser(false, userName);
    }

    /**
     * Finds user details for a user with a given user name.
     *
     * @param userName the username
     * @return the user information. null if such a user does not exist
     */
    public UserDetails findUserDetails(String userName) {
        requireNotEmpty(userName);
        try {
            return userDetailsManager.loadUserByUsername(normalizeUserName(userName));
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    /**
     * Updates the display name and the email
     *
     * @param displayName the display name
     * @param email       the email
     */
    public void updateUser(String displayName, String email) {
        requireNotEmpty(displayName);
        SecurityContext securityContext = getCurrentSecurityContext();
        if (securityContext.getName().equals(SecurityConstants.ANONYMOUS_USER_NAME)) {
            throw new SecurityException("Cannot update user information for anonymous user");
        }
        net.microfalx.bootstrap.security.user.User user = securityContext.getUser();
        securityContexts.remove(normalizeUserName(user.getName()));
        LOGGER.info("Update user profile for '{}', display name '{}', email '{}'", user.getName(), displayName, email);
        userRepository.updateProfile(displayName, email, normalizeUserName(user.getName()));
    }

    /**
     * Returns the entity which contains the user information for the user attached to the web session.
     *
     * @return a non-null instance
     * @throws SecurityException if such a user does not exist
     */
    public User getCurrentUser() {
        User user = findUser(false);
        if (user == null) throw new SecurityException("A user with user name '" + getCurrentUserName()
                + "' could not be located");
        return user;
    }

    /**
     * Returns the current security context, associated with the
     *
     * @return a non-null instance
     */
    public SecurityContext getCurrentSecurityContext() {
        String currentUserName = getCurrentUserName();
        SecurityContext securityContext = securityContexts.get(normalizeUserName(currentUserName));
        if (securityContext != null) return securityContext;
        if (!SecurityConstants.ANONYMOUS_USER_NAME.equals(currentUserName)) {
            User currentUser = findUser(false);
            if (currentUser != null) {
                securityContext = securityContexts.computeIfAbsent(normalizeUserName(currentUser.getUserName()),
                        userName -> new SecurityContextImpl(fromJpa(currentUser), SecurityContextHolder.getContext()));
            }
        }
        return securityContext != null ? securityContext : new SecurityContextImpl();
    }

    /**
     * Returns a setting for the current user.
     *
     * @param name the setting name
     * @return the setting, null if it does not exist
     */
    public <T> T getSetting(String name, Class<T> type) {
        byte[] data = getSetting(getCurrentUser(), name);
        try {
            return Json.asObject(data, type);
        } catch (IOException e) {
            return ExceptionUtils.rethrowExceptionAndReturn(e);
        }
    }

    /**
     * Returns a setting for the current user.
     *
     * @param name the setting name
     * @return the setting, null if it does not exist
     */
    public byte[] getSetting(String name) {
        return getSetting(getCurrentUser(), name);
    }

    /**
     * Returns a setting for a given user.
     *
     * @param user the user
     * @param name the setting name
     * @return the setting, null if it does not exist
     */
    public byte[] getSetting(User user, String name) {
        requireNonNull(user);
        requireNotEmpty(name);
        Optional<UserSetting> setting = userSettingRepository.findById(new UserSetting.Id(normalizeUserName(user.getUserName()), name.toLowerCase()));
        return setting.map(UserSetting::getValue).orElseGet(() -> null);
    }

    /**
     * Changes a setting for the current user.
     *
     * @param name  the setting name
     * @param value the setting value
     */
    public void setSetting(String name, byte[] value) {
        setSetting(getCurrentUser(), name, value);
    }

    /**
     * Changes a setting for the current user.
     *
     * @param name  the setting name
     * @param value the setting value
     */
    public void setSetting(String name, Object value) {
        byte[] data = Json.asBytes(value);
        setSetting(getCurrentUser(), name, data);
    }

    /**
     * Changes a setting for a given user
     *
     * @param user  the user
     * @param name  the setting name
     * @param value the setting value
     */
    public void setSetting(User user, String name, byte[] value) {
        requireNonNull(user);
        requireNotEmpty(name);
        UserSetting setting = new UserSetting();
        setting.setUserName(normalizeUserName(user.getUserName()));
        setting.setName(name.toLowerCase());
        setting.setValue(value);
        setting.setCreatedAt(LocalDateTime.now());
        setting.setModifiedAt(LocalDateTime.now());
        userSettingRepository.saveAndFlush(setting);

    }

    /**
     * Registers an audit action.
     * <p>
     * Information about user is provided by the service.
     *
     * @param context the audit context information
     */
    public void audit(AuditContext context) {
        requireNonNull(context);
        try {
            Audit audit = createAudit(context);
            auditRepository.saveAndFlush(audit);
        } catch (Exception e) {
            LOGGER.atError().setCause(e).log("Failed to audit action '{}' for user '{}', details: {}",
                    context.getAction(), getCurrentUserName(), context.getDescription());
        }
    }

    /**
     * Registers a new user.
     * <p>
     * The user will have no permissions. An administrator will have to assign appropiate permissions to users.
     *
     * @param name     the full name of the user
     * @param userName the user name
     * @param email    the email address
     * @throws SecurityException if the user cannot be registered
     */
    public void register(String name, String userName, String password, String email) {
        requireNotEmpty(name);
        requireNotEmpty(userName);
        createUser(userName, password, name, email, null, USERS_GROUP);
    }

    /**
     * Resets the password of a given user.
     *
     * @param userName the username
     * @return the new temporary password
     */
    public String resetPassword(String userName) {
        requireNotEmpty(userName);
        User user = findUser(false, userName);
        if (user == null) throw new SecurityException("A user with user name '" + userName + "' could not be located");
        String newPassword = getRandomPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPassword(true);
        user.setModifiedAt(LocalDateTime.now());
        userRepository.saveAndFlush(user);
        LOGGER.info("Reset password for user '{}', new temporary password is '{}'", userName, newPassword);
        return newPassword;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        createDefaultUsers();
        createDefaultRoles();
    }

    @PostConstruct
    protected void afterStartup() {
        PreferenceService preferenceService = getBean(PreferenceService.class);
        preferenceService.setStorage(new UserPreferenceListener());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent success) {
        if (success.getAuthentication() instanceof OAuth2LoginAuthenticationToken) {
            createExternalUser(success.getAuthentication());
        }
        AuditContext context = AuditContext.get().setAction(LOGIN_ACTION)
                .setModule(SECURITY_MODULE).setErrorCode("200")
                .setDescription("User '" + success.getAuthentication().getName() + "' was authenticated successfully");
        context = updateAuditContext(context, success.getAuthentication());
        audit(context);
        LOGGER.info("Authenticated user '{}'", getUserName(success.getAuthentication()));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent failures) {
        AuthenticationException exception = failures.getException();
        AuditContext context = AuditContext.get().setAction(LOGIN_ACTION)
                .setModule(SECURITY_MODULE).setErrorCode("403")
                .setDescription("User '" + failures.getAuthentication().getName() + "' failed to authenticate, root cause: "
                        + getRootCauseDescription(exception));
        context = updateAuditContext(context, failures.getAuthentication());
        audit(context);
        String userName = getUserName(failures.getAuthentication());
        Issue issue;
        if (exception instanceof BadCredentialsException) {
            issue = Issue.create(Issue.Type.SECURITY, "Bad Credentials").withDescription("Bad credentials provided for user ''{0}''", failures.getAuthentication().getName());
            LOGGER.warn("Bad credentials provided for user '{}'", userName);
        } else if (exception instanceof UsernameNotFoundException) {
            issue = Issue.create(Issue.Type.SECURITY, "Wrong User")
                    .withDescription("User ''{0}'' could not be found", userName);
            LOGGER.warn("User '{}' could not be found", userName);
        } else if (exception instanceof AuthenticationException) {
            issue = Issue.create(Issue.Type.SECURITY, "Authentication")
                    .withDescription(exception, "Authentication exception for user ''{0}''", userName);
            LOGGER.warn("Authentication exception for user '{}': {}", userName, getRootCauseDescription(exception));
        } else {
            issue = Issue.create(Issue.Type.SECURITY, "Unknown Authentication")
                    .withDescription(exception, "An unknown authentication failure occurred for user ''{0}''", userName);
            LOGGER.atWarn().setCause(exception).log("Unknown authentication failure, user '{}'", userName);
        }
        issue.withModule("User").withSeverity(Issue.Severity.CRITICAL)
                .withAttributeCounter(userName)
                .register();
    }

    @EventListener
    public void onApplicationEvent(ApplicationStartedEvent event) {
        resetAdminPassword();
    }

    @EventListener
    public void onApplicationEvent(LogoutSuccessEvent event) {
        String userName = getUserName(event.getAuthentication());
        if (userName != null) {
            LOGGER.info("Logged out user '{}'", userName);
            securityContexts.remove(normalizeUserName(userName));
        }
    }

    private Audit createAudit(AuditContext context) {
        User user = findUser(true);
        Audit audit = new Audit();
        audit.setUser(user);
        audit.setAction(context.getAction());
        audit.setModule(context.getModule());
        audit.setCategory(context.getCategory());
        audit.setDescription(context.getDescription());
        audit.setErrorCode(context.getErrorCode());
        audit.setReference(context.getReference());
        audit.setClientInfo(context.getClientInfo());
        audit.setCreatedAt(LocalDateTime.now());
        return audit;
    }

    private net.microfalx.bootstrap.security.user.User fromJpa(User jpaUser) {
        UserImpl.UserImplBuilder builder = UserImpl.builder().id(jpaUser.getUserName()).userName(jpaUser.getUserName()).name(jpaUser.getName())
                .email(jpaUser.getEmail()).description(jpaUser.getDescription())
                .enabled(jpaUser.isEnabled()).external(jpaUser.isExternal()).resetPassword(jpaUser.isResetPassword());
        return builder.build();
    }

    private User findUser(boolean create) {
        return findUser(create, getCurrentUserName());
    }

    private User findUser(boolean create, String userName) {
        User user = userRepository.findByUserName(normalizeUserName(userName));
        if (user == null && create) {
            user = createUser(userName, getRandomPassword(), capitalizeWords(userName),
                    null, "A generated user with no permissions for auditing purposes", null);
        }
        return user;
    }

    private void createExternalUser(Authentication authentication) {
        User user = findUser(false, authentication.getName());
        if (user != null || authentication.getPrincipal() == null) return;
        if (authentication instanceof OAuth2LoginAuthenticationToken token) {
            user = new User();
            OAuth2User principal = token.getPrincipal();
            user.setUserName(normalizeUserName(principal.getName()));
            if (principal instanceof ExtendedUserDetails extendedUserDetails) {
                user.setName(extendedUserDetails.getDisplayName());
                user.setEmail(extendedUserDetails.getEmail());
            } else {
                user.setName(user.getUserName());
            }
            user.setPassword(passwordEncoder.encode(getRandomPassword(30)));
            user.setCreatedAt(LocalDateTime.now());
            user.setExternal(true);
            user.setEnabled(true);
            userRepository.saveAndFlush(user);
        }
    }

    private User createUser(String userName, String password, String name, String email, String description, String groupName) {
        if (isEmpty(password)) password = getRandomPassword();
        User user = new User();
        user.setUserName(normalizeUserName(userName));
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setDescription(description);
        user.setCreatedAt(LocalDateTime.now());
        user.setEnabled(true);
        user.setResetPassword(true);
        if (groupName != null) {
            Group group = groupService.findByName(groupName);
            if (group != null) user.setGroups(List.of(group));
        }
        userRepository.saveAndFlush(user);
        LOGGER.info("Created user '{}', temporary password is '{}'", userName, password);
        return user;
    }

    private AuditContext updateAuditContext(AuditContext context, Authentication authentication) {
        context.setReference(SecurityUtils.getUserName(authentication));
        return context;
    }

    private void createDefaultUsers() {
        try {
            if (userRepository.count() >= DEFAULT_USER_COUNT) return;
            createUser(settings.getAdminUserName(), null, capitalizeWords(settings.getAdminUserName()),
                    null, "A default administrator", ADMINISTRATORS_GROUP);
            createUser(settings.getGuestUserName(), null, capitalizeWords(settings.getGuestUserName()),
                    null, "A user with no permissions to access public resources", null);
        } catch (Exception e) {
            LOGGER.error("Failed to create default users", e);
        }
    }

    private void createDefaultRoles() {
        registerRole(Role.ADMIN);
        registerRole(Role.GUEST);
    }

    private void resetAdminPassword() {
        if (!settings.isResetAdmin()) return;
        resetPassword("admin");
    }

    private class UserPreferenceListener implements PreferenceStorage {

        @Override
        public void store(String userName, String name, byte[] value) {
            User user = findUser(true, userName);
            setSetting(user, name, value);
        }

        @Override
        public byte[] load(String userName, String name) {
            User user = findUser(true, userName);
            return getSetting(user, name);
        }
    }

    private class TokenCacheLoader extends CacheLoader<String, UserDetails> {

        @Override
        public UserDetails load(String key) throws Exception {
            User user = userRepository.findByToken(key);
            if (user == null) return null;
            return userDetailsManager.loadUserByUsername(normalizeUserName(user.getUserName()));
        }
    }
}

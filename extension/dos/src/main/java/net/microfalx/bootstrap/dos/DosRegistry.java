package net.microfalx.bootstrap.dos;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Issue;
import net.microfalx.bootstrap.core.utils.CachedAddress;
import net.microfalx.bootstrap.core.utils.GeoLocation;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.lang.TimeUtils;
import net.microfalx.metrics.Timer;
import net.microfalx.threadpool.ThreadPool;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.unmodifiableCollection;
import static net.microfalx.bootstrap.dos.DosUtils.MINIMUM_COUNT;
import static net.microfalx.lang.FormatterUtils.formatDuration;
import static net.microfalx.lang.FormatterUtils.formatThroughput;
import static net.microfalx.lang.StringUtils.capitalize;
import static net.microfalx.lang.StringUtils.formatMessage;
import static net.microfalx.lang.TimeUtils.FIVE_MINUTE;
import static net.microfalx.lang.TimeUtils.millisSince;

/**
 * A registry for DoS operations.
 */
@Slf4j
class DosRegistry {

    private final Map<String, Rule> rulesById = new ConcurrentHashMap<>();
    private final Map<String, Rule> rulesByAddress = new ConcurrentHashMap<>();
    private final Map<String, AddressCounts> addressCounts = new ConcurrentHashMap<>();
    private final Map<String, AddressCounts> persistedAddressCounts = new ConcurrentHashMap<>();
    private final Map<String, ActionCache> actions = new ConcurrentHashMap<>();
    private final Map<String, ActionCache> cidrActions = new ConcurrentHashMap<>();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private DosPersistence persistence;
    private DosProperties properties;
    private ThreadPool threadPool;

    private final AtomicBoolean rulesLoading = new AtomicBoolean();
    private volatile long lastReload = currentTimeMillis();

    private volatile Threshold accessThreshold;
    private volatile Threshold failureThreshold;
    private volatile Threshold notFoundThreshold;
    private volatile Threshold invalidThreshold;
    private volatile Threshold validationThreshold;
    private volatile Threshold securityThreshold;

    Collection<Rule> getRules() {
        return unmodifiableCollection(rulesById.values());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    Collection<RequestCounts> getRequestCounts() {
        Collection values = unmodifiableCollection(addressCounts.values());
        return (Collection<RequestCounts>) values;
    }

    void checkReload() {
        if (millisSince(lastReload) < properties.getReloadInterval().toMillis()) return;
        loadRulesAsync();
    }

    void register(Rule rule) {
        registerById(rule);
        if (rule.getType() == Rule.Type.IP) {
            CachedAddress address = resolve(rule.getAddress());
            registerByAddress(rule, address);
        }
    }

    void register(Request request) {
        CachedAddress address = resolve(request.getAddress());
        final String ip = address.getIp();
        final String hostName = address.getHostname();
        incrementCount(request, address);
        registerClientIfRequired(address, request);
        Rule rule = findRule(ip);
        AddressCounts addressCounts = getCounts(address);
        AddressCount requestCount = addressCounts.increment(request);
        ThresholdViolation thresholdViolation = getThresholdViolation(request, addressCounts);
        Rule.Action action = rule.getAction() == Rule.Action.AUTO ? Rule.Action.DENY : rule.getAction();
        if (action.isApply() && rule.isActive()) {
            ActionCache actionCache = actions.computeIfAbsent(ip, s -> new ActionCache(rule, action));
            if (thresholdViolation.crossed && actionCache.crossed.compareAndSet(false, true)) {
                String message = formatMessage("Client ''{0}'' crossed threshold ''{1}'' for outcome ''{2}'', statistics ''{3}''", address.toDescription(), thresholdViolation.threshold.toDescription(), requestCount.getDescription(), request.getOutcome());
                LOGGER.info(message);
                DosUtils.CROSS.increment(hostName);
                actionCache.update(action, thresholdViolation.reason, thresholdViolation.threshold);
                if (action == Rule.Action.DENY && properties.isEnabled()) {
                    persistence.audit(actionCache.rule, request, actionCache.reason, message);
                    DosUtils.ALERT.increment(hostName);
                }
            } else if (actionCache.isExpired()) {
                removeBan(address, actionCache);
            }
        }
    }

    CachedAddress resolve(String ipOrHost) {
        try (Timer ignored = DosUtils.RESOLVE.startTimer(ipOrHost)) {
            return CachedAddress.get(ipOrHost);
        }
    }

    Rule.Action validate(Request request, CachedAddress address) {
        String ip = address.getIp();
        ActionCache actionCache = getCidrAction(ip);
        if (actionCache == null || actionCache.action == Rule.Action.AUTO || !actionCache.rule.isActive()) {
            actionCache = actions.get(ip);
        }
        if (actionCache == null) return incrementCount(request, Rule.Action.ALLOW);
        if (actionCache.action == Rule.Action.THROTTLE && !actionCache.isExpired()) {
            throttle(actionCache.rule, address);
        }
        Rule.Action action = actionCache.action == Rule.Action.AUTO ? Rule.Action.ALLOW : actionCache.action;
        if (!actionCache.rule.isActive()) action = Rule.Action.ALLOW;
        if (actionCache.action != Rule.Action.ALLOW) {
            if (actionCache.isExpired()) {
                action = removeBan(address, actionCache);
            }
        }
        return incrementCount(request, action);
    }

    Rule findRuleWithCidr(String ip) {
        for (Rule rule : rulesById.values()) {
            if (rule.getType() == Rule.Type.CIDR && rule.matches(ip)) {
                return rule;
            }
        }
        return null;
    }

    Rule findRule(String ipOrHost) {
        return rulesByAddress.get(ipOrHost);
    }

    ActionCache getAction(String ipOrHost) {
        return actions.get(ipOrHost);
    }

    void loadRulesAsync() {
        if (!rulesLoading.get()) threadPool.execute(this::loadRules);
    }

    void incrementCount(Request request, CachedAddress address) {
        DosUtils.REQUEST_HOSTNAME.increment(address.getHostname());
        DosUtils.REQUEST_PROTOCOL.increment(request.getUri().getScheme());
        DosUtils.REQUEST_OUTCOME.increment(request.getOutcome().name().toLowerCase());
    }

    Rule.Action incrementCount(Request request, Rule.Action action) {
        CachedAddress address = resolve(request.getAddress());
        DosUtils.METRICS.withGroup(capitalize(action.name())).increment(address.getHostname());
        if (action == Rule.Action.DENY || action == Rule.Action.THROTTLE) {
            Issue.create(Issue.Type.DOS, address.getHostname()).withDescription(address.toDescription())
                    .withAttributeCounter(request.getUri().getPath())
                    .withSeverity(action == Rule.Action.DENY ? Issue.Severity.CRITICAL : Issue.Severity.NOTICE)
                    .register();
        }
        return action;
    }

    void initialize(DosPersistence persistence, DosProperties properties, ThreadPool threadPool) {
        this.persistence = persistence;
        this.properties = properties;
        this.threadPool = threadPool;
        initThresholds();
        threadPool.scheduleAtFixedRate(new UpdateCountsTask(), properties.getMaintenanceInterval());
        threadPool.scheduleAtFixedRate(new StatsPersistTask(), properties.getStatsUpdateInterval());
    }

    private Rule.Action removeBan(CachedAddress address, ActionCache actionCache) {
        DosUtils.RESET.increment(address.getHostname());
        Rule rule = actionCache.rule;
        LOGGER.info(formatMessage("Client ''{0}'' ban expired, revert back to ''{1}''", address.toDescription(), rule.getAction()));
        return actionCache.update(rule.getAction());
    }

    void registerClientIfRequired(CachedAddress address, Request request) {
        String ip = address.getIp();
        if (!rulesByAddress.containsKey(ip)) {
            Rule rule = Rule.create(ip, Rule.Type.IP).name(address.getHostname()).action(Rule.Action.AUTO).hostName(address.getHostname()).description(address.toDescription()).build();
            persistence.audit(rule, Rule.Reason.DISCOVERY, request);
            register(rule);
        }
    }

    private void registerById(Rule rule) {
        rulesById.put(rule.getId(), rule);
    }

    private void registerByAddress(Rule rule, CachedAddress address) {
        rulesByAddress.put(address.getIp(), rule);
        rulesByAddress.put(address.getHostname(), rule);
        if (!actions.containsKey(rule.getAddress())) {
            actions.put(rule.getAddress(), new ActionCache(rule, rule.getAction()));
        }
    }

    private void loadRules() {
        if (rulesLoading.compareAndSet(false, true)) return;
        try {
            cidrActions.clear();
            actions.clear();
            persistence.getRules().forEach(model -> register(model.toRule()));
            lastReload = System.currentTimeMillis();
        } finally {
            rulesLoading.set(false);
        }
    }

    private ActionCache getCidrAction(String ip) {
        ActionCache actionCache = cidrActions.get(ip);
        if (actionCache != null && !actionCache.isExpired()) return actionCache;
        for (Rule rule : rulesById.values()) {
            if (rule.getType() == Rule.Type.IP) continue;
            if (rule.matches(ip)) {
                actionCache = new ActionCache(rule, rule.getAction());
                cidrActions.put(ip, actionCache);
                break;
            }
        }
        return actionCache;
    }

    ThresholdViolation getThresholdViolation(Request request, AddressCounts counts) {
        if (!properties.isTrackLocalhost() && counts.isLocalhost()) {
            return new ThresholdViolation(false, Rule.Reason.SCAN, Threshold.DEFAULT_THRESHOLD);
        }
        float throughput = counts.getThroughput(request);
        Threshold threshold = Threshold.DEFAULT_THRESHOLD;
        Rule.Reason reason = Rule.Reason.DOS;
        switch (request.getOutcome()) {
            case SUCCESS, NONE:
                threshold = accessThreshold;
                break;
            case NOT_FOUND:
                threshold = notFoundThreshold;
                reason = Rule.Reason.SCAN;
                break;
            case VALIDATION:
                threshold = validationThreshold;
                reason = Rule.Reason.SCAN;
                break;
            case FAILURE:
                threshold = failureThreshold;
                reason = Rule.Reason.SCAN;
                break;
            case INVALID:
                threshold = invalidThreshold;
                reason = Rule.Reason.SCAN;
                break;
            case SECURITY:
            case AUTHENTICATION:
            case AUTHORIZATION:
                threshold = securityThreshold;
                reason = Rule.Reason.SECURITY;
                break;
        }
        boolean crossed = throughput >= threshold.getRequestRate();
        return new ThresholdViolation(crossed, reason, threshold);
    }

    private void throttle(Rule rule, CachedAddress addressCache) {
        Bucket bucket = getBucket(rule, addressCache);
        DosUtils.THROTTLE.increment(addressCache.getIp());
        try {
            bucket.asBlocking().tryConsume(1, properties.getMaximumThrottlingDuration());
        } catch (InterruptedException e) {
            ExceptionUtils.rethrowException(e);
        }
    }

    private Bucket getBucket(Rule rule, CachedAddress address) {
        return buckets.computeIfAbsent(address.getIp(), s -> {
            Bandwidth bandwidth = Bandwidth.simple(Math.max(1, (long) rule.getRequestRateValue()), Duration.ofSeconds(1));
            return Bucket.builder().addLimit(bandwidth).build();
        });
    }

    private AddressCounts getCounts(CachedAddress address) {
        return addressCounts.computeIfAbsent(address.getId(), s -> {
            if (!address.isLocalNetwork()) {
                Issue.create(Issue.Type.DOS, address.getHostname()).withDescription(address.toDescription()).withModule("Registration")
                        .withSeverity(Issue.Severity.NOTICE).withDescription(address.getLocation().getDescription())
                        .register();
            }
            return new AddressCounts(address);
        });
    }

    private void initThresholds() {
        accessThreshold = DosUtils.parseThreshold(properties.getAccessThreshold());
        failureThreshold = DosUtils.parseThreshold(properties.getFailureThreshold());
        invalidThreshold = DosUtils.parseThreshold(properties.getInvalidThreshold());
        notFoundThreshold = DosUtils.parseThreshold(properties.getNotFoundThreshold());
        validationThreshold = DosUtils.parseThreshold(properties.getValidationThreshold());
        securityThreshold = DosUtils.parseThreshold(properties.getSecurityThreshold());
        LOGGER.info("Initialized thresholds: access='{}', not found={}, failure='{}', invalid='{}', validation='{}', security='{}'", accessThreshold.toDescription(), notFoundThreshold.toDescription(), failureThreshold.toDescription(), invalidThreshold.toDescription(), validationThreshold.toDescription(), securityThreshold.toDescription());
    }

    private void persistCounts() {
        for (AddressCounts count : addressCounts.values()) {
            AddressCounts previous = persistedAddressCounts.getOrDefault(count.getId(), new AddressCounts(count.getAddress()));
            AddressCounts current = count.copy(null);
            AddressCounts toPersist = current.copy(previous);
            persistedAddressCounts.put(count.getId(), current);
            Rule rule = findRule(count.getIp());
            if (rule != null) {
                try {
                    if (!toPersist.isEmpty()) persistence.updateStats(rule, toPersist);
                } catch (Exception e) {
                    LOGGER.atError().setCause(e).log("Failed to persist counts for {}", rule.toDescription());
                }
            }
        }
    }

    private void updateCounts() {
        Set<String> ips = new HashSet<>();
        long inactiveIntervalMs = properties.getInactivityInterval().toMillis();
        for (AddressCounts value : addressCounts.values()) {
            if (millisSince(value.updated) > inactiveIntervalMs) ips.add(value.getId());
        }
        StringBuilder logger = new StringBuilder();
        logger.append("Removed inactive IPs:");
        for (String ip : ips) {
            logger.append("  - ").append(ip).append('\n');
            addressCounts.remove(ip);
        }
        if (!ips.isEmpty()) LOGGER.info(logger.toString());
    }

    @Getter
    @ToString
    static class ThresholdViolation {

        private final boolean crossed;
        private final Rule.Reason reason;
        private final Threshold threshold;

        private ThresholdViolation(boolean crossed, Rule.Reason reason, Threshold threshold) {
            this.crossed = crossed;
            this.reason = reason;
            this.threshold = threshold;
        }
    }

    @Getter
    @ToString
    static class ActionCache {

        private final Rule rule;
        private volatile Rule.Action action;
        private volatile Rule.Reason reason = Rule.Reason.DOS;
        private volatile long expireTime;
        private final AtomicBoolean crossed = new AtomicBoolean();

        private ActionCache(Rule rule, Rule.Action action) {
            this.rule = rule;
            this.update(action);
        }

        Rule.Action update(Rule.Action action, Rule.Reason reason, Threshold threshold) {
            update(action);
            this.expireTime = currentTimeMillis() + threshold.getBlockingPeriod().toMillis();
            return action;
        }

        Rule.Action update(Rule.Action action) {
            this.action = action;
            this.crossed.set(false);
            this.expireTime = currentTimeMillis() + FIVE_MINUTE;
            return action;
        }

        boolean isExpired() {
            return expireTime < currentTimeMillis();
        }
    }

    @ToString
    static class AddressCount {

        private final AtomicInteger count = new AtomicInteger();
        private final long created = currentTimeMillis();
        private volatile long updated = created;

        int get() {
            return count.get();
        }

        void increment() {
            updated = currentTimeMillis();
            count.incrementAndGet();
        }

        String getDescription() {
            return formatMessage("request count {0}, throughput ''{1}'', interval {2}", count.get(), formatThroughput(getThroughput(), "r/s"), formatDuration(getDuration()));
        }

        long getDuration() {
            return updated - created;
        }

        float getThroughput() {
            float duration = (updated - created) / 1000f;
            if (duration == 0) return 0;
            return count.get() / duration;
        }
    }

    @ToString
    @Getter
    static class AddressCounts implements RequestCounts {

        private final CachedAddress address;
        private final AddressCount accessCount = new AddressCount();
        private final AddressCount failureCount = new AddressCount();
        private final AddressCount invalidCount = new AddressCount();
        private final AddressCount validationCount = new AddressCount();
        private final AddressCount notFoundCount = new AddressCount();
        private final AddressCount securityCount = new AddressCount();
        private final long created = currentTimeMillis();
        private volatile long updated = created;

        AddressCounts(CachedAddress address) {
            this.address = address;
        }

        @Override
        public String getId() {
            return address.getId();
        }

        @Override
        public String getName() {
            return address.getHostname();
        }

        @Override
        public String getIp() {
            return address.getIp();
        }

        @Override
        public String getCanonicalHostName() {
            return address.getCanonicalHostName();
        }

        @Override
        public GeoLocation getLocation() {
            return address.getLocation();
        }

        public int getAccessCount() {
            return accessCount.get();
        }

        public int getNotFoundCount() {
            return notFoundCount.get();
        }

        public int getFailureCount() {
            return failureCount.get();
        }

        public int getInvalidCount() {
            return invalidCount.get();
        }

        public int getValidationCount() {
            return validationCount.get();
        }

        public int getSecurityCount() {
            return securityCount.get();
        }

        @Override
        public float getThroughput() {
            return accessCount.getThroughput();
        }

        @Override
        public LocalDateTime getCreatedAt() {
            return TimeUtils.toLocalDateTime(created);
        }

        @Override
        public LocalDateTime getModifiedAt() {
            return TimeUtils.toLocalDateTime(updated);
        }

        public CachedAddress getAddress() {
            return address;
        }

        public boolean isLocalhost() {
            return address.isLocalHost();
        }

        AddressCounts copy(AddressCounts previous) {
            AddressCounts copy = new AddressCounts(address);
            copy.accessCount.count.set(this.accessCount.count.get() - (previous != null ? previous.accessCount.get() : 0));
            copy.notFoundCount.count.set(this.notFoundCount.count.get() - (previous != null ? previous.notFoundCount.get() : 0));
            copy.failureCount.count.set(this.failureCount.count.get() - (previous != null ? previous.failureCount.get() : 0));
            copy.invalidCount.count.set(this.invalidCount.count.get() - (previous != null ? previous.invalidCount.get() : 0));
            copy.validationCount.count.set(this.validationCount.count.get() - (previous != null ? previous.validationCount.get() : 0));
            copy.securityCount.count.set(this.securityCount.count.get() - (previous != null ? previous.securityCount.get() : 0));
            return copy;
        }

        boolean isEmpty() {
            return accessCount.get() == 0 && notFoundCount.get() == 0 && failureCount.get() == 0 && invalidCount.get() == 0 && validationCount.get() == 0 && securityCount.get() == 0;
        }

        AddressCount increment(Request request) {
            accessCount.increment();
            AddressCount counter = getCounter(request, false);
            if (counter != null) counter.increment();
            updated = currentTimeMillis();
            return getCounter(request, true);
        }

        AddressCount getCounter(Request request, boolean includeSuccess) {
            return switch (request.getOutcome()) {
                case NOT_FOUND -> notFoundCount;
                case VALIDATION -> validationCount;
                case FAILURE -> failureCount;
                case INVALID -> invalidCount;
                case SECURITY, AUTHENTICATION, AUTHORIZATION -> securityCount;
                default -> includeSuccess ? accessCount : null;
            };
        }

        float getThroughput(Request request) {
            AddressCount counter = getCounter(request, true);
            if (counter.get() < MINIMUM_COUNT) {
                return 0;
            } else {
                return counter.getThroughput();
            }
        }
    }

    class StatsPersistTask implements Runnable {

        @Override
        public void run() {
            persistCounts();
        }
    }

    class UpdateCountsTask implements Runnable {

        @Override
        public void run() {
            updateCounts();
        }
    }
}

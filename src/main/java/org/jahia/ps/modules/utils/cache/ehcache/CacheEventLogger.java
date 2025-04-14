package org.jahia.ps.modules.utils.cache.ehcache;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.event.CacheEventListener;
import net.sf.ehcache.event.NotificationScope;
import org.apache.commons.lang.StringUtils;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.cache.CacheProvider;
import org.jahia.utils.Patterns;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Component(service = CacheEventLogger.class, immediate = true, configurationPid = "cacheutils.eventlogger")
@Designate(ocd = CacheEventLogger.Config.class)
public class CacheEventLogger {
    @ObjectClassDefinition(name = "%configuration.name", description = "%configuration.description", localization = "OSGI-INF/l10n/cacheEventLogger")
    public @interface Config {
        @AttributeDefinition(
                name = "%monitoredCaches.name",
                description = "%monitoredCaches.description"
        )
        String monitoredCachesStr() default "bigEhCacheProvider,HTMLCache,evicted || ehCacheProvider,HTMLNodeUsersACLs,evicted,removed,expired,removeall";

        @AttributeDefinition(name = "%logLevel.name", description = "%logLevel.description",
                options = {
                        @Option(label = "%OFF", value = "OFF"),
                        @Option(label = "%DEBUG", value = "DEBUG"),
                        @Option(label = "%INFO", value = "INFO"),
                        @Option(label = "%WARN", value = "WARN"),
                        @Option(label = "%ERROR", value = "ERROR")
                }
        )
        String logLevel() default "OFF";

        @AttributeDefinition(name = "%useOneLoggerPerCache.name", description = "%useOneLoggerPerCache.description")
        boolean useOneLoggerPerCache() default false;
    }

    private static final Logger logger = LoggerFactory.getLogger(CacheEventLogger.class);

    private final Map<String, Map<String, Collection<CacheEvent>>> monitoredCaches = new HashMap<>();
    private final Map<String, Map<String, CacheEventListener>> listeners = new HashMap<>();
    private boolean useOneLoggerPerCache = false;

    public enum CacheEvent {
        ELEMENT_PUT("Element put in"),
        ELEMENT_UPDATED("Element updated in"),
        ELEMENT_REMOVED("Element removed from"),
        ELEMENT_EXPIRED("Element expired from"),
        ELEMENT_EVICTED("Element evicted from"),
        REMOVE_ALL("Removed all from");

        private final String msg;

        CacheEvent(String msg) {
            this.msg = msg;
        }

        public String getMsg() {
            return msg;
        }
    }

    private CacheEvent fromKey(String key) {
        switch (StringUtils.upperCase(key)) {
            case "REMOVED": return CacheEvent.ELEMENT_REMOVED;
            case "PUT": return CacheEvent.ELEMENT_PUT;
            case "UPDATED": return CacheEvent.ELEMENT_UPDATED;
            case "EXPIRED": return CacheEvent.ELEMENT_EXPIRED;
            case "EVICTED": return CacheEvent.ELEMENT_EVICTED;
            case "REMOVEALL": return CacheEvent.REMOVE_ALL;
            default: return null;
        }
    }

    private void reset() {
        monitoredCaches.clear();
        listeners.clear();
        useOneLoggerPerCache = false;
    }

    @Activate
    public void activate(Config config) {
        reset();

        Pattern.compile(Pattern.quote("||"))
                .splitAsStream(config.monitoredCachesStr())
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .forEach(this::monitorCache);
        useOneLoggerPerCache = config.useOneLoggerPerCache();

        monitoredCaches.forEach((groupName, groupCaches) -> {
            groupCaches.forEach((cacheName, cacheEvents) -> {
                registerListener(groupName, cacheName, cacheEvents, config.logLevel());
            });
        });
        logger.info("Added all the listeners");
    }

    @Deactivate
    public void deactivate() {
        listeners.forEach((groupName, groupCaches) -> {
            groupCaches.forEach((cacheName, listener) -> unregisterListener(groupName, cacheName, listener));
        });
        logger.info("Removed all the listeners");
        reset();
    }

    private void monitorCache(String conf) {
        if (StringUtils.isBlank(conf)) return;
        final String[] items = Patterns.COMMA.split(conf);
        if (items.length < 3) return;
        monitorCache(items[0], items[1], Arrays.stream(items).skip(2).map(StringUtils::trimToNull).map(this::fromKey).filter(Objects::nonNull).toArray(CacheEvent[]::new));
    }

    private void monitorCache(String group, String name, CacheEvent... events) {
        if (!monitoredCaches.containsKey(group)) monitoredCaches.put(group, new HashMap<>());
        monitoredCaches.get(group).put(name, Arrays.asList(events));
    }

    private void registerListener(String cacheGroup, String cacheName, Collection<CacheEvent> cacheEvents, String level) {
        final CacheEventLoggerListener[] listener = new CacheEventLoggerListener[1];
        final boolean sucess = Optional.ofNullable(getCache(cacheGroup, cacheName))
                .map(Ehcache::getCacheEventNotificationService)
                .map(notificationService -> {
                    listener[0] = new CacheEventLoggerListener(cacheEvents, level, getListenerLoggerQualifier(cacheName));
                    if (listener[0].isActive()) {
                        return notificationService.registerListener(listener[0], NotificationScope.ALL);
                    }
                    return false;
                })
                .orElse(false);
        if (sucess) {
            if (!listeners.containsKey(cacheGroup)) listeners.put(cacheGroup, new HashMap<>());
            listeners.get(cacheGroup).put(cacheName, listener[0]);
            logger.info("Registered cache listener on {} -> {}", cacheName, listener[0]);
        }
    }

    private String getListenerLoggerQualifier(String cacheName) {
        if (!useOneLoggerPerCache) return null;
        return Optional.of(cacheName)
                .map(s -> s.contains(".") ? StringUtils.substringAfterLast(s, ".") : s)
                .map(s -> s.replaceAll("[^A-Za-z]+", ""))
                .map(StringUtils::capitalize)
                .orElse(null);
    }

    private void unregisterListener(String cacheGroup, String cacheName, CacheEventListener listener) {
        final boolean success = Optional.ofNullable(getCache(cacheGroup, cacheName))
                .map(Ehcache::getCacheEventNotificationService)
                .map(notificationService -> notificationService.unregisterListener(listener))
                .orElse(false);
    }

    private Ehcache getCache(String group, String name) {
        final CacheProvider cacheProvider;
        try {
            cacheProvider = (CacheProvider) SpringContextSingleton.getBean(group);
        } catch (NoSuchBeanDefinitionException e) {
            logger.error("No cache group named {}", group);
            return null;
        }
        final CacheManager cacheManager = cacheProvider.getCacheManager();
        return cacheManager.getEhcache(name);
    }

}

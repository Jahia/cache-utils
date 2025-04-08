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

@Component(service = {CacheEventLogger.class}, immediate = true, configurationPid = "cacheutils.eventlogger")
@Designate(ocd = CacheEventLogger.Config.class)
public class CacheEventLogger {
    @ObjectClassDefinition(name = "%configuration.name", description = "%configuration.description", localization = "OSGI-INF/l10n/cacheEventLogger")
    public @interface Config {
        @AttributeDefinition(
                name = "%monitoredCaches.name",
                description = "%monitoredCaches.description"
        )
        String[] monitoredCaches() default {"bigEhCacheProvider,HTMLCache,evicted", "ehCacheProvider,HTMLNodeUsersACLs,evicted,removed,expired,removeall"};

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
    }

    private static final Logger logger = LoggerFactory.getLogger(CacheEventLogger.class);

    private final Map<String, Map<String, Collection<CacheEvent>>> monitoredCaches = new HashMap<>();
    private final Map<String, Map<String, CacheEventListener>> listeners = new HashMap<>();

    public enum CacheEvent {
        ELEMENT_REMOVED, ELEMENT_PUT, ELEMENT_UPDATED, ELEMENT_EXPIRED, ELEMENT_EVICTED, REMOVE_ALL
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
    }

    @Activate
    public void activate(Config config) {
        reset();

        Arrays.stream(config.monitoredCaches()).forEach(this::monitorCache);

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
        monitorCache(items[0], items[1], Arrays.stream(items).skip(2).map(StringUtils::trimToNull).filter(Objects::nonNull).map(this::fromKey).toArray(CacheEvent[]::new));
    }

    private void monitorCache(String group, String name, CacheEvent... events) {
        if (!monitoredCaches.containsKey(group)) monitoredCaches.put(group, new HashMap<>());
        monitoredCaches.get(group).put(name, Arrays.asList(events));
    }

    private void registerListener(String cacheGroup, String cacheName, Collection<CacheEvent> cacheEvents, String level) {
        final CacheEventLoggerListener[] listener = new CacheEventLoggerListener[1];
        final boolean sucess = Optional.ofNullable(getCache(cacheGroup, cacheName))
                .map(Ehcache::getCacheEventNotificationService)
                .map(l -> {
                    listener[0] = new CacheEventLoggerListener(cacheEvents, level);
                    if (listener[0].isActive()) {
                        return l.registerListener(listener[0], NotificationScope.ALL);
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

    private void unregisterListener(String cacheGroup, String cacheName, CacheEventListener listener) {
        final boolean sucess = Optional.ofNullable(getCache(cacheGroup, cacheName))
                .map(Ehcache::getCacheEventNotificationService)
                .map(l -> l.unregisterListener(listener))
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

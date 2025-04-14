package org.jahia.ps.modules.utils.cache.ehcache;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.event.CacheEventListener;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.ps.modules.utils.cache.ehcache.CacheEventLogger.CacheEvent;
import org.jahia.utils.Patterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class CacheEventLoggerListener implements CacheEventListener {
    private static final Logger logger = LoggerFactory.getLogger(CacheEventLoggerListener.class);
    private final Logger loggerPut;
    private final Logger loggerUpdated;
    private final Logger loggerRemoved;
    private final Logger loggerExpired;
    private final Logger loggerEvicted;
    private final Logger loggerRemoveAll;

    private final Collection<CacheEvent> cacheEvents;
    private final BiConsumer<Logger, String> logGenerator;
    private final Predicate<Logger> logGeneratorEnabled;
    private final boolean isActive;
    private final String logLevel;

    public CacheEventLoggerListener(Collection<CacheEvent> cacheEvents, String logLevel, String qualifier) {
        this.cacheEvents = new ArrayList<>(cacheEvents);
        loggerPut = getLogger("put", qualifier);
        loggerUpdated = getLogger("updated", qualifier);
        loggerRemoved = getLogger("removed", qualifier);
        loggerExpired = getLogger("expired", qualifier);
        loggerEvicted = getLogger("evicted", qualifier);
        loggerRemoveAll = getLogger("removeAll", qualifier);
        final boolean isValidLogLevel;
        final String lvl = StringUtils.upperCase(logLevel);
        switch (lvl) {
            case "DEBUG":
                logGenerator = Logger::debug;
                logGeneratorEnabled = Logger::isDebugEnabled;
                isValidLogLevel = true;
                this.logLevel = lvl;
                break;
            case "INFO":
                logGenerator = Logger::info;
                logGeneratorEnabled = Logger::isInfoEnabled;
                isValidLogLevel = true;
                this.logLevel = lvl;
                break;
            case "WARN":
                logGenerator = Logger::warn;
                logGeneratorEnabled = Logger::isWarnEnabled;
                isValidLogLevel = true;
                this.logLevel = lvl;
                break;
            case "ERROR":
                logGenerator = Logger::error;
                logGeneratorEnabled = Logger::isErrorEnabled;
                isValidLogLevel = true;
                this.logLevel = lvl;
                break;
            default:
                logGenerator = null;
                logGeneratorEnabled = l -> Boolean.FALSE;
                isValidLogLevel = false;
                this.logLevel = "OFF";
        }
        isActive = isValidLogLevel && CollectionUtils.isNotEmpty(this.cacheEvents);
    }

    public boolean isActive() {
        return isActive;
    }

    private void notify(Ehcache cache, Element element, CacheEvent cacheEvent, Logger out) {
        if (logGeneratorEnabled.test(out) && cacheEvents.contains(cacheEvent)) {
            final String elementDesc = Optional.ofNullable(element)
                    .map(Element::getObjectKey)
                    .map(Object::toString)
                    .map(": "::concat)
                    .orElse(StringUtils.EMPTY);
            logGenerator.accept(out, String.format("%s %s%s", cacheEvent.getMsg(), cache.getName(), elementDesc));
        }
    }
    private static Logger getLogger(String eventType, String qualifier) {
        final StringBuilder loggerName = new StringBuilder();
        loggerName.append(CacheEventLoggerListener.class.getName());
        loggerName.append(".CacheEvent");
        if (qualifier != null) loggerName.append(qualifier);
        loggerName.append(StringUtils.capitalize(eventType));
        return LoggerFactory.getLogger(loggerName.toString());
    }

    @Override
    public void notifyElementRemoved(Ehcache cache, Element element) throws CacheException {
        notify(cache, element, CacheEvent.ELEMENT_REMOVED, loggerRemoved);
    }

    @Override
    public void notifyElementPut(Ehcache cache, Element element) throws CacheException {
        notify(cache, element, CacheEvent.ELEMENT_PUT, loggerPut);
    }

    @Override
    public void notifyElementUpdated(Ehcache cache, Element element) throws CacheException {
        notify(cache, element, CacheEvent.ELEMENT_UPDATED, loggerUpdated);
    }

    @Override
    public void notifyElementExpired(Ehcache cache, Element element) {
        notify(cache, element, CacheEvent.ELEMENT_EXPIRED, loggerExpired);
    }

    @Override
    public void notifyElementEvicted(Ehcache cache, Element element) {
        notify(cache, element, CacheEvent.ELEMENT_EVICTED, loggerEvicted);
    }

    @Override
    public void notifyRemoveAll(Ehcache cache) {
        notify(cache, null, CacheEvent.REMOVE_ALL, loggerRemoveAll);
    }

    @Override
    public void dispose() {
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    @Override
    public String toString() {
        return String.format("Cache listener (level:%s) on : %s", logLevel, StringUtils.join(cacheEvents, Patterns.COMMA.toString()));
    }
}

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
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CacheEventLoggerListener implements CacheEventListener {
    private static final Logger logger = LoggerFactory.getLogger(CacheEventLoggerListener.class);

    private final Collection<CacheEvent> cacheEvents;
    private final Consumer<String> printer;
    private final Supplier<Boolean> printerActive;
    private final boolean isActive;
    private final String logLevel;

    public CacheEventLoggerListener(Collection<CacheEvent> cacheEvents, String logLevel) {
        this.cacheEvents = new ArrayList<>(cacheEvents);
        final boolean isValidLogLevel;
        final String lvl = StringUtils.upperCase(logLevel);
        switch (lvl) {
            case "DEBUG":
                printer = logger::debug;
                printerActive = logger::isDebugEnabled;
                isValidLogLevel = true;
                this.logLevel = lvl;
                break;
            case "INFO":
                printer = logger::info;
                printerActive = logger::isInfoEnabled;
                isValidLogLevel = true;
                this.logLevel = lvl;
                break;
            case "WARN":
                printer = logger::warn;
                printerActive = logger::isWarnEnabled;
                isValidLogLevel = true;
                this.logLevel = lvl;
                break;
            case "ERROR":
                printer = logger::error;
                printerActive = logger::isErrorEnabled;
                isValidLogLevel = true;
                this.logLevel = lvl;
                break;
            default:
                printer = null;
                printerActive = () -> Boolean.FALSE;
                isValidLogLevel = false;
                this.logLevel = "OFF";
        }
        isActive = isValidLogLevel && CollectionUtils.isNotEmpty(this.cacheEvents);
    }

    public boolean isActive() {
        return isActive;
    }

    private void notify(Ehcache cache, Element element, CacheEvent cacheEvent) {
        if (printerActive.get() && cacheEvents.contains(cacheEvent)) {
            final String elementDesc = Optional.ofNullable(element)
                    .map(Element::getObjectKey)
                    .map(Object::toString)
                    .map(": "::concat)
                    .orElse(StringUtils.EMPTY);
            printer.accept(String.format("%s %s%s", cacheEvent.getMsg(), cache.getName(), elementDesc));
        }
    }

    @Override
    public void notifyElementRemoved(Ehcache cache, Element element) throws CacheException {
        notify(cache, element, CacheEvent.ELEMENT_REMOVED);
    }

    @Override
    public void notifyElementPut(Ehcache cache, Element element) throws CacheException {
        notify(cache, element, CacheEvent.ELEMENT_PUT);
    }

    @Override
    public void notifyElementUpdated(Ehcache cache, Element element) throws CacheException {
        notify(cache, element, CacheEvent.ELEMENT_UPDATED);
    }

    @Override
    public void notifyElementExpired(Ehcache cache, Element element) {
        notify(cache, element, CacheEvent.ELEMENT_EXPIRED);
    }

    @Override
    public void notifyElementEvicted(Ehcache cache, Element element) {
        notify(cache, element, CacheEvent.ELEMENT_EVICTED);
    }

    @Override
    public void notifyRemoveAll(Ehcache cache) {
        notify(cache, null, CacheEvent.REMOVE_ALL);
    }

    @Override
    public void dispose() {
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return null;
    }

    @Override
    public String toString() {
        return String.format("Cache listener (level:%s) on : %s", logLevel, StringUtils.join(cacheEvents, Patterns.COMMA.toString()));
    }
}

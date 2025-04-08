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
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CacheEventLoggerListener implements CacheEventListener {
    private static final Logger logger = LoggerFactory.getLogger(CacheEventLoggerListener.class);

    private final Collection<CacheEvent> cacheEvents;
    private final Consumer<String> printer;
    private final Supplier<Boolean> printerActive;
    private final boolean isActive;

    public CacheEventLoggerListener(Collection<CacheEvent> cacheEvents, String logLevel) {
        this.cacheEvents = new ArrayList<>(cacheEvents);
        final boolean isValidLogLevel;
        switch (StringUtils.upperCase(logLevel)) {
            case "DEBUG":
                printer = logger::debug;
                printerActive = logger::isDebugEnabled;
                isValidLogLevel = true;
                break;
            case "INFO":
                printer = logger::info;
                printerActive = logger::isInfoEnabled;
                isValidLogLevel = true;
                break;
            case "WARN":
                printer = logger::warn;
                printerActive = logger::isWarnEnabled;
                isValidLogLevel = true;
                break;
            case "ERROR":
                printer = logger::error;
                printerActive = logger::isErrorEnabled;
                isValidLogLevel = true;
                break;
            default:
                printer = null;
                printerActive = () -> Boolean.FALSE;
                isValidLogLevel = false;
        }
        isActive = isValidLogLevel && CollectionUtils.isNotEmpty(this.cacheEvents);
    }

    public boolean isActive() {
        return isActive;
    }

    private void notify(Ehcache cache, Element element, CacheEvent cacheEvent, String eventDesc) {
        if (printerActive.get() && cacheEvents.contains(cacheEvent)) {
            printer.accept(String.format("Element %s from %s: %s", eventDesc, cache.getName(), element.getObjectKey()));
        }
    }

    @Override
    public void notifyElementRemoved(Ehcache cache, Element element) throws CacheException {
        notify(cache, element, CacheEvent.ELEMENT_REMOVED, "removed");
    }

    @Override
    public void notifyElementPut(Ehcache cache, Element element) throws CacheException {
        notify(cache, element, CacheEvent.ELEMENT_PUT, "put");
    }

    @Override
    public void notifyElementUpdated(Ehcache cache, Element element) throws CacheException {
        notify(cache, element, CacheEvent.ELEMENT_UPDATED, "updated");
    }

    @Override
    public void notifyElementExpired(Ehcache cache, Element element) {
        notify(cache, element, CacheEvent.ELEMENT_EXPIRED, "expired");
    }

    @Override
    public void notifyElementEvicted(Ehcache cache, Element element) {
        notify(cache, element, CacheEvent.ELEMENT_EVICTED, "evicted");
    }

    @Override
    public void notifyRemoveAll(Ehcache cache) {
        if (printerActive.get() && cacheEvents.contains(CacheEvent.REMOVE_ALL)) {
            printer.accept(String.format("Removed all from %s", cache.getName()));
        }
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
        return "Cache listener on : " + StringUtils.join(cacheEvents, Patterns.COMMA.toString());
    }
}

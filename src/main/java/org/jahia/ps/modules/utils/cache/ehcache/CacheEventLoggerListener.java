package org.jahia.ps.modules.utils.cache.ehcache;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.event.CacheEventListener;
import org.apache.commons.lang.StringUtils;
import org.jahia.ps.modules.utils.cache.ehcache.CacheEventLogger.CacheEvent;
import org.jahia.utils.Patterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;

public class CacheEventLoggerListener implements CacheEventListener {
    private static final Logger logger = LoggerFactory.getLogger(CacheEventLoggerListener.class);

    private final Collection<CacheEvent> cacheEvents;

    public CacheEventLoggerListener(Collection<CacheEvent> cacheEvents) {
        this.cacheEvents = new ArrayList<>(cacheEvents);
    }

    private void notify(Ehcache cache, Element element, CacheEvent cacheEvent, String eventDesc) {
        if (logger.isDebugEnabled() && cacheEvents.contains(cacheEvent)) {
            logger.debug("Element {} from {}: {}", eventDesc, cache.getName(), element.getObjectKey());
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
        if (logger.isDebugEnabled() && cacheEvents.contains(CacheEvent.REMOVE_ALL)) {
            logger.debug("Removed all from {}", cache.getName());
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

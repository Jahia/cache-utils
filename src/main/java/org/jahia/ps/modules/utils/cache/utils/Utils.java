package org.jahia.ps.modules.utils.cache.utils;

import org.jahia.osgi.BundleUtils;
import org.jahia.ps.modules.utils.cache.listeners.CacheDependenciesListener;
import org.jahia.services.content.DefaultEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static CacheDependenciesListener getListener() {
        final DefaultEventListener listener = BundleUtils.getOsgiService(DefaultEventListener.class, "(CacheDependenciesListener=true)");
        if (!(listener instanceof CacheDependenciesListener)) {
            logger.error("Impossible to resolve the dependencies listener");
            return null;
        }

        return (CacheDependenciesListener) listener;
    }
}

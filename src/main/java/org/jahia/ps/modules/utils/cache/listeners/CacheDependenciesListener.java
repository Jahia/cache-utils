package org.jahia.ps.modules.utils.cache.listeners;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.services.cache.CacheHelper;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRItemWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component(service = DefaultEventListener.class, property = {"CacheDependenciesListener=true"})
public class CacheDependenciesListener extends DefaultEventListener {

    private static final Logger logger = LoggerFactory.getLogger(CacheDependenciesListener.class);

    private final static int NODE_EVENTS = IntStream.of(Event.NODE_ADDED, Event.NODE_REMOVED, Event.NODE_MOVED).sum();
    private final static int PROPERTY_EVENTS = IntStream.of(Event.PROPERTY_ADDED, Event.PROPERTY_REMOVED, Event.PROPERTY_CHANGED).sum();
    public static final String FS_CACHE_FILENAME = "mod-cache-dependencies.txt";
    public static final String JAVA_IO_TMPDIR = "java.io.tmpdir";
    public static final String STRUCTURE_VERSION = "fs cache structure V1";

    private final Map<String, Set<String>> watchedNodeTypesMapping = new ConcurrentHashMap<>();
    private final Map<String, String> pathMapping = new ConcurrentHashMap<>();

    public CacheDependenciesListener() {
        setWorkspace(Constants.LIVE_WORKSPACE);
        setAvailableDuringPublish(true);
    }

    @Activate
    public void start() {
        final File fsCache = getFsCache();
        if (!fsCache.exists()) return;
        final List<String> fileContent;
        try {
            fileContent = FileUtils.readLines(fsCache, "UTF-8");
        } catch (IOException e) {
            logger.error("", e);
            return;
        }
        if (fileContent.size() == 0) {
            logger.warn("Empty file sytem cache file");
            return;
        }
        if (!StringUtils.equals(fileContent.get(0), STRUCTURE_VERSION)) {
            logger.warn("Incompatible version of the file system cache file, skipping it. The output cache might require to be flushed manually");
            return;
        }
        fileContent.remove(0);
        for (String line : fileContent) {
            final String[] items = StringUtils.split(line);
            final String key = items[0];
            if (StringUtils.equals(key, "watchedNodeTypesMapping")) {
                if (items.length < 3) {
                    logger.error("Invalid line: {}", line);
                    continue;
                }
                final String nt = items[1];
                for (int i =2; i<items.length; i++) {
                    addWatchedNodeType(nt, items[i]);
                }
            } else if (StringUtils.equals(key, "pathMapping")) {
                if (items.length != 3) {
                    logger.error("Invalid line: {}", line);
                    continue;
                }
                pathMapping.put(items[1], items[2]);
            } else {
                logger.error("Unexpected key {}", key);
            }
        }
        FileUtils.deleteQuietly(fsCache);
    }

    @Deactivate
    public void stop() {
        final File fsCache = getFsCache();
        if (fsCache.exists()) {
            logger.error("The file system cache file already exists, overriding it");
        }
        final List<String> fileContent = new ArrayList<>();
        fileContent.add(STRUCTURE_VERSION);
        watchedNodeTypesMapping.entrySet().stream()
                .map(e -> String.format("watchedNodeTypesMapping %s %s", e.getKey(), String.join(" ", e.getValue())))
                .forEach(fileContent::add);
        pathMapping.entrySet().stream()
                .map(e -> String.format("pathMapping %s %s", e.getKey(), e.getValue()))
                .forEach(fileContent::add);
        try {
            FileUtils.writeLines(fsCache, "UTF-8", fileContent);
        } catch (IOException e) {
            logger.error("Impossible to write the file content", e);
        }
    }

    private File getFsCache() {
        return new File(System.getProperty(JAVA_IO_TMPDIR), FS_CACHE_FILENAME);
    }

    @Override
    public int getEventTypes() {
        return NODE_EVENTS + PROPERTY_EVENTS;
    }

    @Override
    public String[] getNodeTypes() {
        /*
          TODO : review this after QA-14535 is fixed
          We should not need to return JAHIANT_TRANSLATION here.
         */
        return new String[]{Constants.JAHIANT_CONTENT, Constants.JAHIANT_TRANSLATION};
    }

    @Override
    public void onEvent(EventIterator events) {
        final Set<String> pathToFlush = new HashSet<>();
        final Set<String> processedNodes = new HashSet<>();
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, session -> {
                while (events.hasNext()) {
                    collectPathToFlush(events.nextEvent(), session, pathToFlush, processedNodes);
                }
                return null;
            });
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        if (!pathToFlush.isEmpty()) {
            // TODO : this will propagate on the cluster, but there's no way to prevent it
            CacheHelper.flushOutputCachesForPaths(pathToFlush, false);
        }
    }

    private void collectPathToFlush(Event event, JCRSessionWrapper session, Collection<String> pathToFlush, Collection<String> processedNodes)  {
        final JCRNodeWrapper node;
        final String nodePath;
        final JCRItemWrapper item;
        String itemPath = null;
        try {
            itemPath = event.getPath();
            item = session.getItem(itemPath);
            if (item.isNode()) {
                node = (JCRNodeWrapper) item;
                nodePath = itemPath;
            } else {
                node = item.getParent();
                nodePath = node.getPath();
            }
            if (processedNodes.contains(nodePath)) return;
            processedNodes.add(nodePath);
        } catch (RepositoryException e) {
            // some properties are added and removed, such as WIP, locks, ...
            logger.debug("Impossible to load the item {}", itemPath);
            return;
        }
        watchedNodeTypesMapping.forEach((type, deps) -> {
            try {
                if (!node.isNodeType(type)) return;
            } catch (RepositoryException e) {
                logger.error("", e);
                return;
            }
            deps.stream()
                    .map(pathMapping::get)
                    .filter(Objects::nonNull)
                    .forEach(pathToFlush::add);
        });
    }

    public void addDependency(JCRNodeWrapper node, String type) {
        try {
            final String uuid = node.getIdentifier();
            pathMapping.put(uuid, node.getPath());
            addWatchedNodeType(type, uuid);
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    private void addWatchedNodeType(String type, String uuid) {
        if (!watchedNodeTypesMapping.containsKey(type)) watchedNodeTypesMapping.put(type, new HashSet<>());
        watchedNodeTypesMapping.get(type).add(uuid);
    }
}
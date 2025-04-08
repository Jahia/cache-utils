<a href="https://store.jahia.com/contents/modules-repository/org/jahia/ps/modules/cache-utils.html">
    <img src="https://www.jahia.com/modules/jahiacom-templates/images/jahia-3x.png" alt="Jahia logo" title="Jahia" align="right" height="60" />
</a>

# <a name="summary"></a>Cache Utils

Jahia module which provides cache related utilities.

## EhCache events monitoring

To monitor the cache events on some caches, edit the OSGi configuration named `Ehcache events logger`.

Configure each tag to monitor, specifying the cache group, the cache name, and then the events to be logged. Examples:
```
bigEhCacheProvider,HTMLCache,evicted
ehCacheProvider,HTMLNodeUsersACLs,evicted,removed,expired,removeall
```

Possible events:
- put : an element is added to the cache
- updated : an element is updated in the cache
- removed : an element is removed from the cache
- expired : an element is removed from the cache because it has reached its expiration condition 
- evicted : an element is removed from the cache by the cache framework itself to make some room to new elements
- removeall : the whole cache is purged, removing all elements at the same time

## Taglib

Update the `pom.xml` file of your module before using the taglib:

```
<dependencies>
    <dependency>
        <groupId>org.jahia.ps.modules</groupId>
        <artifactId>cache-utils</artifactId>
        <version>2.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>jahia-ps-public</id>
        <url>https://devtools.jahia.com/nexus/content/repositories/jahia-professional-services-public-repository</url>
    </repository>
</repositories>
```
  
Add the following tag to the JSP scripts which produce fragments that need to be invalidated from the cache when modifications
are performed on some nodes which are not displayed as subfragment in the previous version of the output.

```
<cache:addNodeTypeBasedCacheDependency nodeTypes="jnt:news" path="${currentNode.resolveSite.path}" />
```

The tag relies on a JCR listener which is disabled by default. To enable it, edit the OSGi configuration named `Cache dependencies listener`

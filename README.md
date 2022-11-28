<a href="https://store.jahia.com/contents/modules-repository/org/jahia/ps/modules/cache-utils.html">
    <img src="https://www.jahia.com/modules/jahiacom-templates/images/jahia-3x.png" alt="Jahia logo" title="Jahia" align="right" height="60" />
</a>

# <a name="summary"></a>Cache Utils

Jahia module which provides cache related utilities.

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

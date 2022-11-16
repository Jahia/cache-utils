package org.jahia.ps.modules.utils.cache.taglibs;

import org.apache.commons.lang.StringUtils;
import org.jahia.osgi.BundleUtils;
import org.jahia.ps.modules.utils.cache.listeners.CacheDependenciesListener;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.render.RenderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;

public class AddNodeTypeBasedCacheDependencyTag extends TagSupport {

    private static final Logger logger = LoggerFactory.getLogger(AddNodeTypeBasedCacheDependencyTag.class);

    private JCRNodeWrapper node;
    private String nodeTypes;

    public void setNode(JCRNodeWrapper node) {
        this.node = node;
    }

    public void setNodeTypes(String nodeTypes) {
        this.nodeTypes = nodeTypes;
    }

    private void resetState() {
        node = null;
        nodeTypes = null;
    }

    @Override
    public int doEndTag() throws JspException {
        final RenderContext renderContext = (RenderContext) pageContext.getRequest().getAttribute("renderContext");
        if (!renderContext.isLiveMode()) {
            return super.doEndTag();
        }

        if (node == null) {
            node = (JCRNodeWrapper) pageContext.getRequest().getAttribute("currentNode");
        }
        if (node == null) {
            throw new JspTagException("Impossible to add dependency to a null node");
        }

        if (StringUtils.isBlank(nodeTypes)) {
            logger.error("No node type specified, impossible to add a dependency for the node {}", node.getPath());
            return super.doEndTag();
        }

        final DefaultEventListener listener = BundleUtils.getOsgiService(DefaultEventListener.class, "(CacheDependenciesListener=true)");
        if (!(listener instanceof CacheDependenciesListener)) {
            logger.error("Impossible to resolve the dependencies listener");
        } else {
            final CacheDependenciesListener dependenciesListener = (CacheDependenciesListener) listener;
            for (String nt : StringUtils.split(nodeTypes)) {
                dependenciesListener.addDependency(node, nt);
            }
        }

        resetState();
        return super.doEndTag();
    }
}

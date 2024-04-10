/*
 * Copyright 2020 Advanced Software Technologies Lab at ETH Zurich, Switzerland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.commands.monkey.ape.tree;

import static com.android.commands.monkey.ape.utils.Config.alwaysIgnoreWebView;
import static com.android.commands.monkey.ape.utils.Config.alwaysIgnoreWebViewAction;
import static com.android.commands.monkey.ape.utils.Config.computeImageText;
import static com.android.commands.monkey.ape.utils.Config.excludeEmptyChild;
import static com.android.commands.monkey.ape.utils.Config.excludeInvisibleNode;
import static com.android.commands.monkey.ape.utils.Config.ignoreWebViewThreshold;
import static com.android.commands.monkey.ape.utils.Config.patchGUITree;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.android.commands.monkey.ape.utils.Logger;
import com.android.commands.monkey.ape.utils.StringCache;
import com.android.commands.monkey.ape.utils.Utils;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

public class GUITreeBuilder {


    static Pattern BOUNDS_RECT = Pattern.compile("\\[([0-9]+),([0-9]+)]\\[([0-9]+),([0-9]+)]");

    public static final String GUI_TREE_NODE_TAG_NAME = "node";
    public static final String GUI_TREE_NODE_PROP_NAME = "GUITreeNode";
    public static final String GUI_TREE_PROP_NAME = "GUITree";

    public static GUITree getGUITree(Document document) {
        return (GUITree) document.getUserData(GUI_TREE_PROP_NAME);
    }

    public static GUITreeNode getGUITreeNode(Node domNode) {
        return (GUITreeNode) domNode.getUserData(GUI_TREE_NODE_PROP_NAME);
    }

    static Rect parseRect(String bounds) {
        Matcher m = BOUNDS_RECT.matcher(bounds);
        if (m.matches()) {
            return new Rect(Integer.valueOf(m.group(1)), Integer.valueOf(m.group(2)), Integer.valueOf(m.group(3)),
                    Integer.valueOf(m.group(4)));
        }
        return null;
    }

    static Rect toBoundsInParent(Rect screen, Rect parentScreen) {
        return new Rect(screen.left - parentScreen.left, screen.top - parentScreen.top,
                screen.right - parentScreen.right, screen.bottom - parentScreen.bottom);
    }

    private Document document;
    private GUITree tree;
    private ComponentName activity;

    private GUITreeBuilder(ComponentName activity) {
        this.activity = activity;
    }

    public GUITreeBuilder(ComponentName activity, AccessibilityNodeInfo rootInfo, Bitmap bitmap) {
        this(activity);
        buildGUITree(rootInfo, bitmap);
    }

    public GUITreeBuilder(ComponentName activity, String xmlFile) {
        this(activity);
        buildGUITree(xmlFile);
    }

    public GUITreeBuilder(ComponentName activity, Document document) {
        this(activity);
        buildGUITree(document);
    }

    /**
     * For rebuild a new GUI tree from the given GUI tree
     * @param nm
     * @param tree
     */
    public GUITreeBuilder(GUITree tree) {
        this(tree.getActivityName());
        this.document = tree.getDocument();
        this.tree = tree;
        rebuildGUITree();
    }

    protected GUITree buildGUITree(AccessibilityNodeInfo rootInfo, Bitmap bitmap) {
        GUITreeNode rootNode = buildNodeAndXmlFromNodeInfo(rootInfo, bitmap);
        tree = new GUITree(rootNode, activity);
        tree.setDocument(document);
        return tree;
    }

    private void rebuildGUITree() {
        tree.setCurrentState(null);
    }

    protected GUITree buildGUITree(Document document) {
        this.document = document;
        GUITreeNode rootNode = buildNodeFromXml(document);
        tree = new GUITree(rootNode, activity);
        tree.setDocument(document);
        return tree;
    }

    protected GUITree buildGUITree(String fileName) {
        try {
            document = Utils.readXml(fileName);
            return buildGUITree(document);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot build ther GUI tree from " + fileName);
        }
    }

    protected GUITreeNode buildNodeAndXmlFromNodeInfo(AccessibilityNodeInfo info, Bitmap image) {
        document = createDocument();
        GUITreeNode root = buildNodeAndXmlFromNodeInfo(null, document, info, 0);
        if (document != null) {
            document.appendChild(root.getDomNode());
        }
        if (patchGUITree) {
            patchGUITree(root);
        }
        if (computeImageText) {
            if (image != null) {
                computeImageText(root, image);
                image.recycle();
            }
        }
        if (alwaysIgnoreWebViewAction && !alwaysIgnoreWebView) {
            ignoreActionsInWebView(root, root.isWebView());
        }
        return root;
    }

    /**
     * An ImageButton may not have a text attribute. We compute a hash value from its bytes as its text.
     * @param node
     * @param image
     */
    void computeImageText(GUITreeNode node, final Bitmap image) {
        if (image == null) {
            return;
        }
        if (node.getChildCount() > 0) {
            Iterator<GUITreeNode> it = node.getChildren();
            while (it.hasNext()) {
                computeImageText(it.next(), image);
            }
        } else {
            if (node.getText() == null || node.getText().length() == 0) {
                node.computeAndSetImageText(image);
            }
        }
    }

    private void patchGUITree(GUITreeNode node) {
        if (node.getChildCount() == 0) {
            return;
        }
        if (node.isWebView()) {
            return;
        }
        Rect nodeBounds = node.getBoundsInScreen();
        Rect childrenBounds = new Rect();
        Iterator<GUITreeNode> nodeIt = node.getChildren();
        while (nodeIt.hasNext()) {
            GUITreeNode child = nodeIt.next();
            childrenBounds.union(child.getBoundsInScreen());
        }
        if (nodeBounds.contains(childrenBounds)) {
            if (doPatchingChildren(node)) {
                // Logger.iprintln("Patching this node: " + node.getClassName() + "@" + node.getResourceID() + "@"
                //         + node.getText());
                nodeIt = node.getChildren();
                while (nodeIt.hasNext()) {
                    GUITreeNode child = nodeIt.next();
                    if (!child.isClickable()) {
                        // Logger.iprintln("Patching child node: " + child.getClassName() + "@" + child.getResourceID()
                        // + "@" + child.getText());
                        child.setClickable(true);
                        if (node.getChildCount() == 1) {
                            if (node.getIndex() != child.getIndex()) {
                                child.setIndex(node.getIndex());
                            }
                        }
                    }
                }
                if (childrenBounds.contains(nodeBounds.centerX(), nodeBounds.centerY())) {
                    node.setClickable(false);
                }
            }
        }
        // Keep patching until leaf nodes.
        nodeIt = node.getChildren();
        while (nodeIt.hasNext()) {
            patchGUITree(nodeIt.next());
        }
    }

    private boolean sameRow(Iterator<GUITreeNode> nodeIt) {
        int top = -1;
        int bot = -1;
        while (nodeIt.hasNext()) {
            GUITreeNode next = nodeIt.next();
            if (top == -1) {
                top = next.getBoundsInScreen().top;
            } else if (top != next.getBoundsInScreen().top) {
                return false;
            }
            if (bot == -1) {
                bot = next.getBoundsInScreen().bottom;
            } else if (bot != next.getBoundsInScreen().bottom) {
                return false;
            }
        }
        return true;
    }

    private boolean sameColumn(Iterator<GUITreeNode> nodeIt) {
        int left = -1;
        int right = -1;
        while (nodeIt.hasNext()) {
            GUITreeNode next = nodeIt.next();
            if (right == -1) {
                right = next.getBoundsInScreen().right;
            } else if (right != next.getBoundsInScreen().right) {
                return false;
            }
            if (left == -1) {
                left = next.getBoundsInScreen().left;
            } else if (left != next.getBoundsInScreen().left) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sometimes the action handler of all siblings may be registered to the container.
     * This method identifies such cases heuristically. Once such a case is found,
     * it will copy all action attributes from the parent container to its children.
     * @param node
     * @return
     */
    private boolean doPatchingChildren(GUITreeNode node) {
        if (!node.isClickable()) {
            return false;
        }
        if (node.getChildCount() == 0) {
            return false;
        }
        if (node.getChildCount() == 1) {
            return true;
        }
        if (sameRow(node.getChildren())) {
            return true;
        }
        if (sameColumn(node.getChildren())) {
            return true;
        }
        return false;
    }

    private void ignoreActionsInWebView(GUITreeNode node, boolean ignore) {
        if (ignore) {
            if (node.isClickable()) {
                node.setClickable(false);
            }
            if (node.isCheckable()) {
                node.setCheckable(false);
            }
            if (node.isLongClickable()) {
                node.setLongClickable(false);
            }
            if (node.isScrollable()) {
                node.setScrollable(false);
            }
            if (node.isClickable() || node.isCheckable() || node.isLongClickable() || node.isScrollable()) {
                throw new IllegalStateException("Should be cleared!");
            }
            if (node.getDomNode() == null) {
                throw new RuntimeException();
            }
        }
        if (node.isWebView()) {
            ignore = true;
        }
        if (node.getChildCount() == 0) {
            return;
        }
        Iterator<GUITreeNode> nodeIt = node.getChildren();
        while (nodeIt.hasNext()) {
            GUITreeNode child = nodeIt.next();
            ignoreActionsInWebView(child, ignore);
        }
    }

    public static Document buildDocumentFromGUITree(GUITree tree) {
        Document document = createDocument();
        document.appendChild(treeNodeToXmlNode(document, tree.getRootNode()));
        return document;
    }

    private static Element treeNodeToXmlNode(Document document, GUITreeNode node) {
        Element xml = createNodeElement(document, node);
        Iterator<GUITreeNode> iterator = node.getChildren();
        while (iterator.hasNext()) {
            GUITreeNode child = iterator.next();
            xml.appendChild(treeNodeToXmlNode(document, child));
        }
        return xml;
    }

    protected GUITreeNode buildNodeAndXmlFromNodeInfo(GUITreeNode parent, org.w3c.dom.Node parentXML,
            AccessibilityNodeInfo info, int index) {
        int childCount = info.getChildCount();
        GUITreeNode node = new GUITreeNode(parent);
        node.setIndex(index);
        fillNode(node, info);
        Element xml = null;
        if (parentXML != null) {
            xml = createNodeElement(node);
            node.setDomNode(xml);
        }
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = info.getChild(i);
            GUITreeNode childNode;
            if (child == null) {
                if (!excludeEmptyChild) {
                    childNode = GUITreeNode.buildEmptyNode(node);
                    if (xml != null) {
                        Element xmlChild = createNodeElement();
                        childNode.setDomNode(xmlChild);
                    }
                    node.addChild(childNode);
                }
            } else if (!child.isVisibleToUser()) {
                if (!excludeInvisibleNode) {
                    childNode = buildNodeAndXmlFromNodeInfo(node, xml, child, i);
                    if (alwaysIgnoreWebView && childNode.isWebView()) {
                        continue;
                    }
                    node.addChild(childNode);
                }
            } else {
                childNode = buildNodeAndXmlFromNodeInfo(node, xml, child, i);
                if (alwaysIgnoreWebView && childNode.isWebView()) {
                    continue;
                }
                node.addChild(childNode);
            }
        }
        if (!alwaysIgnoreWebView && node.isWebView()) {
            checkAndRemoveWebView(node);
        }
        return node;
    }

    protected boolean checkAndRemoveWebView(GUITreeNode node) {
        int countNodesWithAction = node.getDescendantCount(); // count(node, actionNodeFilter);
        if (countNodesWithAction > ignoreWebViewThreshold) {
            Logger.iformat("Too many nodes in WebView (%d > %d), remove the WebView.", countNodesWithAction,
                    ignoreWebViewThreshold);
            node.clearChildren();
            return true;
        }
        return false;
    }

    protected GUITreeNode buildNodeFromXml(Document document) {
        GUITreeNode root = buildNodeFromXml(null, 0, getFirstChildElement(document.getDocumentElement()));
        return root;
    }

    private GUITreeNode buildNodeFromXml(GUITreeNode parent, int index, Element e) {
        GUITreeNode n = new GUITreeNode(parent);
        n.setIndex(Integer.valueOf(e.getAttribute("index")));
        n.setResourceID(e.getAttribute("resource-id"));
        n.setClassName(e.getAttribute("class"));
        n.setPackageName(e.getAttribute("package"));
        n.setText(e.getAttribute("text"));

        n.setClickable(Boolean.valueOf(e.getAttribute("clickable")));
        n.setLongClickable(Boolean.valueOf(e.getAttribute("long-clickable")));
        n.setCheckable(Boolean.valueOf(e.getAttribute("checkable")));
        n.setScrollable(Boolean.valueOf(e.getAttribute("scrollable")));

        n.setFocusable(Boolean.valueOf(e.getAttribute("focusable")));
        n.setFocused(Boolean.valueOf(e.getAttribute("focused")));

        n.setChecked(Boolean.valueOf(e.getAttribute("checked")));
        n.setEnabled(Boolean.valueOf(e.getAttribute("enabled")));

        Rect bounds = parseRect(e.getAttribute("bounds"));
        n.setBoundsInScreen(bounds);
        if (parent == null) {
            n.setBoundsInParent(bounds);
        } else {
            n.setBoundsInParent(toBoundsInParent(bounds, parent.getBoundsInScreen()));
        }

        // patch scroll-type
        e.setAttribute("scroll-type", n.getScrollType());

        NodeList nl = e.getChildNodes(); // this list include text and other
        // nodes.
        int ci = 0;
        for (int i = 0; i < nl.getLength(); i++) {
            org.w3c.dom.Node xml = nl.item(i);
            if (xml instanceof Element) {
                n.addChild(buildNodeFromXml(n, ci++, (Element) xml));
            }
        }
        return n;
    }

    static Set<String> editTextWidgets = new HashSet<>();

    static {
        editTextWidgets.add("android.widget.EditText");
        editTextWidgets.add("android.inputmethodservice.ExtractEditText");
        editTextWidgets.add("android.widget.AutoCompleteTextView");
        editTextWidgets.add("android.widget.MultiAutoCompleteTextView");
    }

    public static boolean isEditText(String cls) {
        return editTextWidgets.contains(cls);
    }

    private static Document createDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder parser = factory.newDocumentBuilder();
            return parser.newDocument();
        } catch (ParserConfigurationException e) {
            Logger.wformat("Fail to create XML document");
        }
        return null;
    }

    private Element createNodeElement() {
        return document.createElement(GUI_TREE_NODE_TAG_NAME);
    }

    static void fillElement(Element element, GUITreeNode node) {
        element.setAttribute("index", StringCache.cacheString(String.valueOf(node.getIndex())));
        element.setAttribute("text", node.getText());
        element.setAttribute("resource-id", node.getResourceID());
        element.setAttribute("class", node.getClassName());
        element.setAttribute("content-desc", node.getContentDesc());
        element.setAttribute("package", node.getPackageName());
        element.setAttribute("checkable", Boolean.toString(node.isCheckable()));
        element.setAttribute("checked", Boolean.toString(node.isChecked()));
        element.setAttribute("clickable", Boolean.toString(node.isClickable()));
        element.setAttribute("enabled", Boolean.toString(node.isEnabled()));
        element.setAttribute("focusable", Boolean.toString(node.isFocusable()));
        element.setAttribute("focused", Boolean.toString(node.isFocused()));
        element.setAttribute("scrollable", Boolean.toString(node.isScrollable()));
        element.setAttribute("long-clickable", Boolean.toString(node.isLongClickable()));
        element.setAttribute("password", Boolean.toString(node.isPassword()));

        element.setAttribute("scroll-type", node.getScrollType());

        node.setDomNode(element);
    }

    private static Element createNodeElement(Document document, GUITreeNode node) {
        Element element = document.createElement(GUI_TREE_NODE_TAG_NAME);
        fillElement(element, node);
        return element;
    }

    private Element createNodeElement(GUITreeNode node) {
        return createNodeElement(document, node);
    }

    private void fillNode(GUITreeNode node, AccessibilityNodeInfo info) {
        node.setNodeInfo(info);
        node.setPackageName(StringCache.cacheStringEmptyOnNull(info.getPackageName()));
        node.setClassName(StringCache.cacheStringEmptyOnNull(info.getClassName()));
        node.setText(StringCache.cacheStringEmptyOnNull(StringCache.truncateText(StringCache.removeQuotes(info.getText())), true));
        node.setResourceID(StringCache.cacheStringEmptyOnNull(info.getViewIdResourceName()));
        node.setContentDesc(StringCache.cacheStringEmptyOnNull(StringCache.removeQuotes(info.getContentDescription()), true));

        node.setChecked(info.isChecked());
        node.setEnabled(info.isEnabled());

        node.setCheckable(info.isCheckable());
        node.setClickable(info.isClickable());
        node.setLongClickable(info.isLongClickable());
        node.setScrollable(info.isScrollable());
        node.setFocusable(info.isFocusable());

        node.setFocused(info.isFocused());

        Rect temp = new Rect();
        info.getBoundsInScreen(temp);
        node.setBoundsInScreen(temp);
        info.getBoundsInParent(temp);
        node.setBoundsInParent(temp);
    }

    private Element getFirstChildElement(Element e) {
        NodeList nl = e.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            org.w3c.dom.Node n = nl.item(i);
            if (n instanceof Element) {
                return (Element) n;
            }
        }
        return null;
    }

    public GUITree getGUITree() {
        return this.tree;
    }

    public Document getXmlDocument() {
        return this.document;
    }

}

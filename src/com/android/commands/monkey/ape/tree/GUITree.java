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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.android.commands.monkey.ape.Agent;
import com.android.commands.monkey.ape.model.ModelAction;
import com.android.commands.monkey.ape.model.UtbState;
import com.android.commands.monkey.ape.utils.Logger;
import com.android.commands.monkey.ape.utils.Utils;

import android.content.ComponentName;

public class GUITree implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 7853671636037436555L;

    private static final boolean debug = false;

    private static List<GUITree> loadedGUITrees = new ArrayList<GUITree>();

    public static void releaseLoadedData() {
        for (GUITree tree : loadedGUITrees) {
            tree.releaseData();
        }
        loadedGUITrees.clear();
    }

    private static void registerLoadedData(GUITree tree) {
        loadedGUITrees.add(tree);
    }

    private static AtomicInteger counter = new AtomicInteger(0);

    private int id;
    private int timestamp;

    private final GUITreeNode rootNode;
    private final String activityClassName;
    private final String activityPackageName;

    private UtbState currentState;

    private Object[] currentNodes; // An element of this array may be a node or an array of nodes

    private transient Document document;

    public GUITree(GUITreeNode guiTree, ComponentName activityName) {
        this.id = counter.getAndIncrement();
        this.rootNode = guiTree;
        this.activityPackageName = activityName.getPackageName();
        this.activityClassName = activityName.getClassName();
    }

    public ComponentName getActivityName() {
        return new ComponentName(activityPackageName, activityClassName);
    }

    public String getActivityClassName() {
        return activityClassName;
    }

    public GUITreeNode getRootNode() {
        return rootNode;
    }

    public boolean isIsomorphicTo(GUITree tree) {
        throw new RuntimeException("Not implemented");
    }

    public boolean hasFocusedNode() {
        boolean focused = false;
        outer: for (Object nodeOrNodes : currentNodes) {
            if (nodeOrNodes instanceof GUITreeNode) {
                GUITreeNode node = (GUITreeNode) nodeOrNodes;
                if (node.isFocused()) {
                    focused = true;
                    break;
                }
            } else {
                GUITreeNode[] node = (GUITreeNode[]) nodeOrNodes;
                for (GUITreeNode n : node) {
                    if (n.isFocused()) {
                        break outer;
                    }
                }
            }
        }
        return focused;
    }

    public Document getDocument() {
        if (document == null) {
            Logger.iformat("Rebuild document for tree #%d", this.getTimestamp());
            document = GUITreeBuilder.buildDocumentFromGUITree(this);
            setDocument(document);
        }
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
        if (document != null) {
            registerLoadedData(this);
            document.setUserData(GUITreeBuilder.GUI_TREE_PROP_NAME, this, null);
        }
    }

    void rebuild(Object[] nodes) {
        this.currentNodes = nodes;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public void setCurrentState(UtbState state) {
        if (state == null) {
            this.currentState = null;
            return;
        }
        this.currentState = state;
    }

    public UtbState getCurrentState() {
        return this.currentState;
    }

    /**
     * For debugging only, need to traverse the whole GUI tree.
     * @param node
     * @return
     */
    public boolean containsHeavy(GUITreeNode node) {
        return search(rootNode, node);
    }

    private boolean search(final GUITreeNode root, final GUITreeNode node) {
        if (root == node) {
            return true;
        }
        if (root.getChildCount() == 0) {
            return false;
        }
        Iterator<GUITreeNode> it = root.getChildren();
        while (it.hasNext()) {
            if (search(it.next(), node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Release the XML document to save memory since we can recreate it when necessary.
     */
    public void releaseData() {
        Logger.dprintln("Release document for tree #" + getTimestamp());
        releaseNodeData(this.rootNode);
        this.document = null;
    }

    private static void releaseNodeData(GUITreeNode node) {
        Element e = node.getDomNode();
        e.setUserData(GUITreeBuilder.GUI_TREE_NODE_PROP_NAME, null, null);
        node.setDomNode(null);
        node.setNodeInfo(null);
        Iterator<GUITreeNode> iterator = node.getChildren();
        while (iterator.hasNext()) {
            releaseNodeData(iterator.next());
        }
    }

    public String toString() {
        return "GUITree[" + this.id + "@" + this.timestamp + "]@" + currentState;
    }

    public Object[] getCurrentNodes() {
        return currentNodes;
    }
}

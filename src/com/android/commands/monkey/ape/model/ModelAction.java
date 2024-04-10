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
package com.android.commands.monkey.ape.model;

import org.json.JSONException;
import org.json.JSONObject;

import com.android.commands.monkey.ApeRRFormatter;
import com.android.commands.monkey.ape.tree.GUITree;
import com.android.commands.monkey.ape.tree.GUITreeAction;
import com.android.commands.monkey.ape.tree.GUITreeNode;
import com.android.commands.monkey.ape.utils.Config;

import android.graphics.Rect;

public class ModelAction extends Action {

    private static final long serialVersionUID = 6905861873801801029L;
    private static final int saturatedVisitedThreshold = 2;

    // Resolution information
    private final UtbState state;
    
    //private final Name target;
    
    //private int resovledTimestamp = -1;
    //private GUITreeNode[] resolvedNodes;
    private final GUITreeNode resolvedNode;
    private GUITreeAction resolvedGUITreeAction;
    private float resolvedSaturation;
    private GUITree resolvedTree;

    public ModelAction(UtbState state, ActionType type) {
        this(state, null, type);
    }

    public ModelAction(UtbState state, GUITreeNode target, ActionType type) {
        super(type);
        this.state = state;
        this.resolvedNode = target;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((state == null) ? 0 : state.hashCode());
        //result = prime * result + ((target == null) ? 0 : target.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        ModelAction other = (ModelAction) obj;
        if (state == null) {
            if (other.state != null)
                return false;
        } else if (!state.equals(other.state))
            return false;
        if (resolvedNode == null) {
            if (other.resolvedNode != null)
                return false;
        } else if (!resolvedNode.equals(other.resolvedNode))
            return false;
        return true;
    }

    public UtbState getState() {
        return state;
    }

    public boolean isSaturated() {
        if (!requireTarget()) {
            return this.isVisited();
        }
        return this.resolvedSaturation >= 1.0F;
    }

    /*public boolean isOverAbstracted() {
        if (!requireTarget()) {
            return false;
        }
        if (resolvedNodes == null) {
            throw new RuntimeException("Action is not resolved.");
        }
        return resolvedNodes.length >= Config.actionRefinmentThreshold;
    }

    public Name getTarget() {
        return target;
    }*/

    public String toString() {
        return super.toString()
                + (resolvedNode != null ? resolvedNode : (state != null ? state.toString() : ""));
    }

    /*protected String resolvedInfo() {
        if (!requireTarget() || resolvedNode == null) {
            return super.resolvedInfo();
        }
        Rect bounds = resolvedNode.getBoundsInScreen();
        return String.format("%s[S=%f][RN=%d][%d,%d,%d,%d][%s]", super.resolvedInfo(), resolvedSaturation,
                resolvedNodes.length, bounds.left, bounds.top, bounds.right, bounds.bottom,
                resolvedNode.getText());
    }*/

    public String toFullString() {
        return toString();
    }

    /*public boolean isResolvedAt(int timestamp) {
        if (this.resovledTimestamp != timestamp) {
            return false;
        }
        if (!requireTarget()) {
            return true;
        }
        return this.resolvedNode != null;
    }*/

    public GUITreeAction getResolvedGUITreeAction() {
        return this.resolvedGUITreeAction;
    }

    public void setResolvedGUITreeAction(GUITreeAction resolvedGUITreeAction) {
        this.resolvedGUITreeAction = resolvedGUITreeAction;
    }

    public GUITree getResolvedGUITree() {
        return this.resolvedTree;
    }

    /*public void resolveAt(int timestamp, int throttle, GUITree tree, GUITreeNode node, GUITreeNode[] nodes) {
        super.setThrottle(throttle);
        this.resovledTimestamp = timestamp;
        this.resolvedTree = tree;
        this.resolvedGUITreeAction = new GUITreeAction(tree, node, this);
        this.resolvedGUITreeAction.setThrottle(throttle);
        if (!requireTarget()) {
            return;
        }
        this.resolvedNode = node;
        this.resolvedNodes = nodes;
        if (nodes.length == 0) {
            throw new IllegalStateException("Fail to resolve a node for this action: " + this);
        }
        if (nodes.length == 1) {
            this.resolvedSaturation = this.isVisited() ? 1.0F : 0.0F;
        } else {
            float total = Math.min(nodes.length, saturatedVisitedThreshold);
            this.resolvedSaturation = Math.min(1.0F, this.visitedCount / total);
        }
        return;
    }*/

    public GUITreeNode getResolvedNode() {
        return this.resolvedNode;
    }

    /*public GUITreeNode[] getResolvedNodes() {
        return this.resolvedNodes;
    }*/

    public float getResolvedSaturation() {
        if (!requireTarget()) {
            return isVisited() ? 1.0F : 0;
        }
        return Math.min(Math.max(0, this.resolvedSaturation), 1.0F);
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject jAction = super.toJSONObject();
        GUITreeNode node = getResolvedNode();
        if (requireTarget()) {
            jAction.put("target", node.toString());
        }
        if (node != null) {
            Rect bounds = node.getBoundsInScreen();
            jAction.put("bounds", ApeRRFormatter.formatRect(bounds));
            String inputText = node.getInputText();
            if (inputText != null) {
                jAction.put("inputText", inputText);
            }
        }
        return jAction;
    }
}

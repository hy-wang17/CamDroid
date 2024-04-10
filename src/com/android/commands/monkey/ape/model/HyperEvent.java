package com.android.commands.monkey.ape.model;

import java.util.List;

import com.android.commands.monkey.ape.tree.GUITreeNode;

import android.content.ComponentName;

public class HyperEvent {
	
	private GUITreeNode node;
	private ComponentName activity;
	private List<Action> allowedActions;
	
	public HyperEvent(GUITreeNode node, ComponentName activity, List<Action> allowedActions) {
		this.node = node;
		this.activity = activity;
		this.allowedActions = allowedActions;
	}
	
	public GUITreeNode getNode() {
		return this.node;
	}
	
	public ComponentName getActivity() {
		return this.activity;
	}
	
	public List<Action> getAllowedActions() {
		return this.allowedActions;
	}
	
	@Override
	public String toString() {
		String res =  this.node.getText() + "_"
				+ this.node.getResourceID() + "_"
				+ this.node.getClassName() + "_"
				+ this.activity.toString() + "_";
		for (Action act : this.allowedActions) {
			if (act.getType().equals(ActionType.MODEL_CLICK)) {
				res += "Clickable!";
			}
			if (act.getType().equals(ActionType.MODEL_LONG_CLICK)) {
			    res += "LongClickable!";
			}
			if (act.isScroll()) {
			    res += "Scrollable!";
			}
		}
		return res;
	}

}

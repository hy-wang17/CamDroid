package com.android.commands.monkey.ape.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.android.commands.monkey.ape.tree.GUITree;
import com.android.commands.monkey.ape.tree.GUITreeNode;
import com.android.commands.monkey.ape.utils.Utils;

import android.content.ComponentName;

public class UtbState {
    public Action backAction;
    private ComponentName activity;
    private GUITree currentGUITree;
    
    // text, resource_id, class_name, description, activity, allowed_actions
    private List<HyperEvent> hevents;
    private List<String> hstrs;
    private Map<String, HyperEvent> strEventMap;
    
    public UtbState(ComponentName activity, GUITree gTree) {
    	this.activity = activity;
    	this.currentGUITree = gTree;
    	backAction = new Action(ActionType.MODEL_BACK);
    	this.hevents = new ArrayList<>();
    	this.hstrs = new ArrayList<>();
    	this.strEventMap = new HashMap<>();
    	GUITreeNode root = gTree.getRootNode();
    	generateHyperEvents(root);
    	for (HyperEvent e : this.hevents) {
        	this.hstrs.add(e.toString());
			this.strEventMap.put(e.toString(), e);
        }
//		if(hstrs.contains("立即更新_cn.com.shbank.mper:id/tv_right_android.widget.TextView_ComponentInfo{cn.com.shbank.mper/com.yitong.mobile.biz.launcher.app.main.MainHomeActivity}_Clickable!")
//				&& hstrs.contains("下次再说_cn.com.shbank.mper:id/tv_left_android.widget.TextView_ComponentInfo{cn.com.shbank.mper/com.yitong.mobile.biz.launcher.app.main.MainHomeActivity}_Clickable!")){
//			hstrs.remove("立即更新_cn.com.shbank.mper:id/tv_right_android.widget.TextView_ComponentInfo{cn.com.shbank.mper/com.yitong.mobile.biz.launcher.app.main.MainHomeActivity}_Clickable!");
//			strEventMap.remove("立即更新_cn.com.shbank.mper:id/tv_right_android.widget.TextView_ComponentInfo{cn.com.shbank.mper/com.yitong.mobile.biz.launcher.app.main.MainHomeActivity}_Clickable!");
//		}
		Utils.removeDuplication(this.hstrs);
		this.hevents = new ArrayList<>(this.strEventMap.values());
    }
    
    private void generateHyperEvents(GUITreeNode node) {
    	
    	// Logger.format("***UTB*** Detected GUI Node: text:%s, resourceId:%s, className:%s,"
    	// 		+ " desc:%s, checkable:%s, clickable:%s, longClickable:%s, scrollType:%s",
    	// 		node.getText(), node.getResourceID(), node.getClassName(), node.getContentDesc(),
    	// 		node.isCheckable(), node.isClickable(), node.isLongClickable(), node.getScrollType());
    	
    	Iterator<GUITreeNode> nodeIter = node.getChildren();
    	
    	ModelAction scrollBU;
		ModelAction scrollLR;
    	ModelAction scrollRL;
    	ModelAction scrollTD;
    	
    	while (nodeIter.hasNext()) {
    		GUITreeNode child = nodeIter.next();
    		List<Action> allowedActions = new ArrayList<Action>();
    		if (child.isCheckable() || child.isClickable()) {
    			ModelAction click = new ModelAction(this, child, ActionType.MODEL_CLICK);
    			allowedActions.add(click);
    		}
    		if (child.isLongClickable()) {
    			ModelAction longClick = new ModelAction(this, child, ActionType.MODEL_LONG_CLICK);
    			allowedActions.add(longClick);
    		}
    		switch (child.getScrollType()) {
    		case "all":
    			scrollBU = new ModelAction(this, child, ActionType.MODEL_SCROLL_BOTTOM_UP);
    			scrollLR = new ModelAction(this, child, ActionType.MODEL_SCROLL_LEFT_RIGHT);
    	    	scrollRL = new ModelAction(this, child, ActionType.MODEL_SCROLL_RIGHT_LEFT);
    	    	scrollTD = new ModelAction(this, child, ActionType.MODEL_SCROLL_TOP_DOWN);
    			allowedActions.add(scrollBU);
    			allowedActions.add(scrollLR);
    			allowedActions.add(scrollRL);
    			allowedActions.add(scrollTD);
    			break;
    		case "horizontal":
    			scrollLR = new ModelAction(this, child, ActionType.MODEL_SCROLL_LEFT_RIGHT);
    	    	scrollRL = new ModelAction(this, child, ActionType.MODEL_SCROLL_RIGHT_LEFT);
    			allowedActions.add(scrollLR);
    			allowedActions.add(scrollRL);
    			break;
    		case "vertical":
    			scrollBU = new ModelAction(this, child, ActionType.MODEL_SCROLL_BOTTOM_UP);
    			scrollTD = new ModelAction(this, child, ActionType.MODEL_SCROLL_TOP_DOWN);
    			allowedActions.add(scrollBU);
    			allowedActions.add(scrollTD);
    			break;
    		default:
    		}
    		if (!allowedActions.isEmpty()) {
    			HyperEvent hevent = new HyperEvent(child, this.activity, allowedActions);
				if (!this.hevents.contains(hevent)){
					this.hevents.add(hevent);
				}
    		}
    		generateHyperEvents(child);
    	}
	}
    
    public ComponentName getActivity() {
    	return this.activity;
    }
    
    public void setActivity(ComponentName activity) {
    	this.activity = activity;
    }
    
    public GUITree getGUITree() {
    	return this.currentGUITree;
    }
    
    public void setGUITree(GUITree gTree) {
    	this.currentGUITree = gTree;
    }
    
    public List<HyperEvent> getHyperEvents(){
    	return this.hevents;
    }
    
    public void addHyperEvent(HyperEvent hevent) {
    	this.hevents.add(hevent);
    }
    
    public List<String> getHstrs(){
    	return this.hstrs;
    }
    
    public Map<String, HyperEvent> getStrEventMap() {
    	return this.strEventMap;
    }
    
}

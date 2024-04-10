package com.android.commands.monkey.ape.agent;

import static com.android.commands.monkey.ape.utils.Config.saveGUITreeToXmlEveryStep;
import static com.android.commands.monkey.ape.utils.Config.takeScreenshot;
import static com.android.commands.monkey.ape.utils.Config.takeScreenshotForEveryStep;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

// import ;

import com.android.commands.monkey.MonkeySourceApe;
import com.android.commands.monkey.ape.Agent;
import com.android.commands.monkey.ape.BadStateException;
import com.android.commands.monkey.ape.StopTestingException;
import com.android.commands.monkey.ape.model.Action;
import com.android.commands.monkey.ape.model.ActionType;
import com.android.commands.monkey.ape.model.HyperEvent;
import com.android.commands.monkey.ape.model.UtbState;
import com.android.commands.monkey.ape.tree.GUITree;
import com.android.commands.monkey.ape.tree.GUITreeBuilder;
import com.android.commands.monkey.ape.tree.GUITreeNode;
import com.android.commands.monkey.ape.utils.Config;
import com.android.commands.monkey.ape.utils.Logger;
import com.android.commands.monkey.ape.utils.Utils;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;
import org.json.JSONException;

public class UtbAgent implements Agent {
	
	protected MonkeySourceApe ape;
    protected int timestamp;
    protected GUITree newGUITree;
    protected UtbState utbState;
    
    private List<String> testedActivities;
    private Map<String,List<String>> activityEventDict;
    private Map<String,Integer> eventNumDict;
    private Map<String,Integer> eventNumForExpDict;
    private Map<String,Integer> activityNumDict;
    
    // Record hevent history.
    private List<HyperEvent> heventsHistory;
    private HyperEvent lastEvent;
    private List<String> lastEventList;
	private String lastActivity;
//	private boolean internalUpdateFlag;
	private boolean checkActivityFlag;
	private int loginStep;
	private int loginTotalStep;
	private String loginPassword;
	private List<String> loginInput;

	private Map<String, Map<String,Integer>> counter;
    private Map<String, Map<String,Double>> probModel;
    
    // Prob model & Q-learning parameters
    private Map<String, Double> qTable;
	private int n;
	private double beta;
	private double alpha;
	private double gamma;
	private int numberUp;

    private final long beginMillis;
    private Set<String> activityNames;
    
    private int lastBadStateCount;
    private int badStateCounter;
    private int totalBadStates;

	private List<String> heventListForQ; // the size of the list is n.
	private List<Double> rewardList; // the size of the list is n.
	private List<List<String>> lastStateList; // the size of the list is n.
	private List<List<String>> nextStateList; // the size of the list is n.
	private List<String> lastActList; // the size of the list is n.
	private List<String> nextActList; // the size of the list is n.
	
	public UtbAgent(MonkeySourceApe ape) {
		this.ape = ape;
		beginMillis = System.currentTimeMillis();
		this.testedActivities = new ArrayList<>();
    	this.activityEventDict = new HashMap<>();
    	this.eventNumDict = new HashMap<>();
    	this.eventNumForExpDict = new HashMap<>();
    	this.activityNumDict = new HashMap<>();
    	this.heventsHistory = new ArrayList<>();
		this.lastActivity = null;
//		this.internalUpdateFlag = true;
		this.checkActivityFlag = true;

		this.activityNames = new HashSet<>();
		this.heventListForQ = new ArrayList<>();
		this.rewardList = new ArrayList<>();
		this.lastStateList = new ArrayList<>();
		this.nextStateList = new ArrayList<>();
		this.lastActList = new ArrayList<>();
		this.nextActList = new ArrayList<>();
    	
    	this.counter = new HashMap<>();
    	this.probModel = new HashMap<>();
    	this.lastEvent = null;
		this.lastEventList = new ArrayList<>();
		this.qTable = new HashMap<>();
    	this.n = 3;
    	this.beta = 0.1;
    	this.alpha = 0.8;
    	this.gamma = 0.5;
    	this.numberUp = 1;
		this.loginPassword = Config.get("utb.loginPassword");
		this.loginStep = 1;
		this.loginInput = new ArrayList<>();
		for(int i = 0; i < this.loginPassword.length(); i++){
			char ch = this.loginPassword.charAt(i);
			if(i == 0 && Character.isDigit(ch)){
				this.loginInput.add("123");
			}
			if (i>0 && Character.isLetter(this.loginPassword.charAt(i-1)) && Character.isDigit(ch)){
				this.loginInput.add("123");
			}
			if (i>0 && Character.isDigit(this.loginPassword.charAt(i-1)) && Character.isLetter(ch)){
				this.loginInput.add("ABC");
			}
			this.loginInput.add(String.valueOf(ch));
		}
		this.loginTotalStep = this.loginInput.size() + 3;
    }

	public UtbAgent(MonkeySourceApe ape, File utbStateFile) throws FileNotFoundException {
		// s = "{\"probs_dict\": {},\"tested_acts\": [],\"act_event_dict\": {},\"event_num_dict\": {},\"act_num_dict\": {}}"
		this.ape = ape;
		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader(new FileReader(utbStateFile));

			String tempString = null;
			String laststr = "";

			while ((tempString = bufferedReader.readLine()) != null) {
				laststr = laststr + tempString;
			}
//			Map<String, Object> map = new HashMap<String, Object>();
//			ForJsonParse jsonParse = JSONObject.parseObject(laststr, ForJsonParse.class);
//			this.activityEventDict = jsonParse.activityEventDict;
//			this.eventNumDict = jsonParse.eventNumDict; //put
//			this.probModel = jsonParse.probModel; //put
//			this.qTable = jsonParse.qTable; //put
			JSONObject jsonObject=new JSONObject(laststr);
//			Gson gson = new Gson();
//			ForJsonParse jsonParse = gson.fromJson(laststr, ForJsonParse.class);
			this.activityEventDict = Utils.parseMapKeyStringValueList(jsonObject.getJSONObject("activityEventDict"));
			this.eventNumDict = Utils.parseMapKeyStringValueInt(jsonObject.getJSONObject("eventNumDict"));
			this.qTable = Utils.parseMapKeyStringValueDouble(jsonObject.getJSONObject("qTable"));
			this.probModel = Utils.parseMapKeyStringValueMap(jsonObject.getJSONObject("probModel")); //put
//			this.probModel = (Map<String, Map<String, Double>>) jsonObject.get("probModel");

//			map =
		} catch (IOException | JSONException e) {
			e.printStackTrace();
			this.activityEventDict = new HashMap<>(); //put
			this.eventNumDict = new HashMap<>(); //put
			this.probModel = new HashMap<>(); //put
			this.qTable = new HashMap<>(); //put
		}finally{
			if (bufferedReader != null) {
				try {
					  //不管执行是否出现异常，必须确保关闭BufferedReader
					  bufferedReader.close();
				} catch (IOException e1) {
					e1.printStackTrace();
			  }
			}
		}
		beginMillis = System.currentTimeMillis();
		this.testedActivities = new ArrayList<>();
    	// this.activityEventDict = new HashMap<>(); //put
    	// this.eventNumDict = new HashMap<>(); //put
    	this.eventNumForExpDict = new HashMap<>();
    	this.activityNumDict = new HashMap<>();
    	this.heventsHistory = new ArrayList<>();
		this.checkActivityFlag = true;
		this.lastActivity = null;

		this.activityNames = new HashSet<>();
		this.heventListForQ = new ArrayList<>();
		this.rewardList = new ArrayList<>();
		this.lastStateList = new ArrayList<>();
		this.nextStateList = new ArrayList<>();
		this.lastActList = new ArrayList<>();
		this.nextActList = new ArrayList<>();
    	
    	this.counter = new HashMap<>();
    	// this.probModel = new HashMap<>(); //put
    	this.lastEvent = null;
		this.lastEventList = new ArrayList<>();
		// this.qTable = new HashMap<>(); //put
    	this.n = 3;
    	this.beta = 0.1;
    	this.alpha = 0.8;
    	this.gamma = 0.5;
    	this.numberUp = 1;

		this.loginPassword = Config.get("utb.loginPassword");
		this.loginStep = 1;
		this.loginInput = new ArrayList<>();
		for(int i = 0; i < this.loginPassword.length(); i++){
			char ch = this.loginPassword.charAt(i);
			if(i == 0 && Character.isDigit(ch)){
				this.loginInput.add("123");
			}
			if (i>0 && Character.isLetter(this.loginPassword.charAt(i-1)) && Character.isDigit(ch)){
				this.loginInput.add("123");
			}
			if (i>0 && Character.isDigit(this.loginPassword.charAt(i-1)) && Character.isLetter(ch)){
				this.loginInput.add("ABC");
			}
			this.loginInput.add(String.valueOf(ch));
		}
		this.loginTotalStep = this.loginInput.size() + 3;
    }
	
	static enum UtbEventType {
        TRIVIAL_ACTIVITY,
        SATURATED_STATE, USE_BUFFER, EARLY_STAGE,
        EPSILON_GREEDY, RANDOM, NULL, BUFFER_LOSS, FILL_BUFFER, BAD_STATE;
    }
	
	public List<String> getTestedActivities(){
    	return this.testedActivities;
    }
    
    public Map<String,List<String>> getActivityEventDict(){
    	return this.activityEventDict;
    }
    
    public Map<String,Integer> getEventNumDict(){
    	return this.eventNumDict;
    }
    
//    public Map<ComponentName,Integer> getActivityNumDict(){
//    	return this.activityNumDict;
//    }
    
//    public List<HyperEvent> getHyperEventsHistory(){
//    	return this.heventsHistory;
//    }
    
//    public Map<String, Map<ComponentName,Integer>> getCounter(){
//    	return this.counter;
//    }
    
//    public Map<String, Map<ComponentName,Double>> getProbModel(){
//    	return this.probModel;
//    }
    
//    public void addTestedActivity(ComponentName testedAct) {
//    	this.testedActivities.add(testedAct);
//    }
    
//    public void addHyperEventHistory(HyperEvent hevent) {
//    	this.heventsHistory.add(hevent);
//    }
//
//    public void addActivityEventKVPair(ComponentName act, List<String> hevent) {
//    	this.activityEventDict.put(act.toString(), hevent);
//    }
//
//    public void replaceActivityEventKVPair(ComponentName act, List<String> hevent) {
//    	this.activityEventDict.replace(act.toString(), hevent);
//    }
//
//    public void addEventNumKVPair(String hevent, Integer i) {
//    	this.eventNumDict.put(hevent, i);
//    }
//
//    public void replaceEventNumKVPair(String hevent, Integer i) {
//    	this.eventNumDict.replace(hevent, i);
//    }
    
//    public void addActivityNumKVPair(ComponentName act, Integer i) {
//    	this.activityNumDict.put(act, i);
//    }
//
//    public void replaceActivityNumKVPair(ComponentName act, Integer i) {
//    	this.activityNumDict.replace(act, i);
//    }
    
    // Q-Learning
    // Update Q-table
	Double getBonusRewardByPBRS(double gamma, List<String> lastEvents, String aLast,
					  List<String> nextEvents, String aNext,
				Map<String, Map<String, Double>> probModel, List<String> testedActivities,
				Map<String, Integer> activityNumDict, Map<String, Integer> eventNumDict) {
		List<String> pmk = new ArrayList<>(probModel.keySet());
		List<String> enk = new ArrayList<>(eventNumDict.keySet());
		int nH = nextEvents.size() - (Utils.intersect(nextEvents, pmk)).size();
		int nHLast = lastEvents.size() - (Utils.intersect(lastEvents, pmk)).size();
		List<String> npi = Utils.intersect(nextEvents, pmk);
		List<String> npiLast = Utils.intersect(lastEvents, pmk);
		int nC = npi.size() - Utils.intersect(npi, enk).size();
		int nCLast = npiLast.size() - Utils.intersect(npiLast, enk).size();
		Double eeSum = 0.0, eeSumLast = 0.0;
		for (String e : nextEvents) {
			if (probModel.containsKey(e)) {
				for (String c: probModel.get(e).keySet()) {
					if (!(testedActivities.contains(c))) {
						eeSum += probModel.get(e).get(c);
					}
				}
			}
		}

		for (String e : lastEvents) {
			if (probModel.containsKey(e)) {
				for (String c: probModel.get(e).keySet()) {
					if (!(testedActivities.contains(c))) {
						eeSumLast += probModel.get(e).get(c);
					}
				}
			}
		}

		// nc == 0 & eeSum == 0 during the first testing
		double v = (double)nH + 0.5* (double)nC + eeSum;
		double vLast = (double)nHLast + 0.5* (double)nCLast + eeSumLast;

		// r = nh / Math.sqrt(this.activityNumDict.get(aNext) + 1);
		if(activityNumDict.containsKey(aNext) && activityNumDict.containsKey(aLast))
			return (gamma * v / Math.sqrt(activityNumDict.get(aNext) + 1)) -
					(vLast / Math.sqrt(activityNumDict.get(aLast) + 1));
		else
			return (gamma * v) - vLast;
	}

	Double getR(String event, List<String> nextEvents, String aNext,
				Map<String, Map<String, Double>> probModel, List<String> testedActivities,
				Map<String, Integer> activityNumDict, Map<String, Integer> eventNumDict) {
		List<String> pmk = new ArrayList<>(probModel.keySet());
		List<String> enk = new ArrayList<>(eventNumDict.keySet());
		int nH = nextEvents.size() - (Utils.intersect(nextEvents, pmk)).size();
		List<String> npi = Utils.intersect(nextEvents, pmk);
		int nC = npi.size() - Utils.intersect(npi, enk).size();
		Double eeSum = 0.0;
		for (String e : nextEvents) {
			if (probModel.containsKey(e)) {
				for (String c: probModel.get(e).keySet()) {
					if (!(testedActivities.contains(c))) {
						eeSum += probModel.get(e).get(c);
					}
				}
			}
		}
		// nc == 0 & eeSum == 0 during the first testing
		double v = (double)nH + 0.5* (double)nC + eeSum;
		double eet = 0.0;
		// eet == 0 during the first testing
		for (String c : probModel.get(event).keySet()) {
			if (!(testedActivities.contains(c))) {
				eet += probModel.get(event).get(c);
			}
		}

		// r = nh / Math.sqrt(this.activityNumDict.get(aNext) + 1);
		if(activityNumDict.containsKey(aNext))
			return eet / Math.sqrt(eventNumDict.get(event) + 1)
				+ v / Math.sqrt(activityNumDict.get(aNext) + 1);
		else
			return eet / Math.sqrt(eventNumDict.get(event) + 1) + v;
	}
	
	public String getNextEvent(List<String> nextEvents) {
		Map<String, Double> tmpDict = new HashMap<>();
		for (String e : nextEvents) {
			if (this.qTable.containsKey(e)) {
				tmpDict.put(e, this.qTable.get(e));
			}
		}
		if (tmpDict.keySet().isEmpty()) {
			return null;
		}
		List<Map.Entry<String, Double>> listSort = new ArrayList<>(tmpDict.entrySet());
		String event = listSort.get(0).getKey();
		for (String e : tmpDict.keySet()) {
			if (tmpDict.get(e) > tmpDict.get(event)) {
				event = e;
			}
		}
//		Collections.sort(listSort, (o1, o2)->((int)(o2.getValue() - o1.getValue())));
//		HyperEvent event = listSort.get(0).getKey();
		return event;
	}
	
	public String getNextActivity(Map<String,Double> activityProb) {
		double x = Math.random();
		double cumuliProb = 0.0;
		for (String item : activityProb.keySet()) {
			cumuliProb += activityProb.get(item);
			if (x < cumuliProb)
				return item;
		}
		return null;
	}
	
//    public Double calNStepSarsa(String event, String topComp, List<String> hstrList) {
//    	double gT = 0.0;
//		Map<String, Map<String, Integer>> counterTmp = new HashMap<>(counter);
//		Map<String, Map<String,Double>> probModelTmp = new HashMap<>(probModel);
//		Map<String,Integer> activityNumDictTmp = new HashMap<>(activityNumDict);
//		Map<String,Integer> eventNumDictTmp = new HashMap<>(eventNumDict);
//		List<String> testedActivitiesTmp = new ArrayList<>(testedActivities);
//
//		for (int i = 0; i < this.n; i++) {
//    		if (!(probModelTmp.containsKey(event)))
//    			break;
//			else if (i == 0){
//				gT += this.getR(event, hstrList, topComp, probModelTmp,
//						testedActivitiesTmp, activityNumDictTmp, eventNumDictTmp);
//				event = getNextEvent(hstrList);
//			}
//    		else {
//    			if(probModelTmp.get(event).isEmpty())
//    				break;
//    			String aNext = getNextActivity(probModelTmp.get(event));
//				// Logger.format("***UTB*** update qTable, get next activity %s", aNext.toString());
//				// Logger.println("***UTB*** update qTable, activityEventDict" + activityEventDict.toString());
//    			if (!(this.activityEventDict.containsKey(aNext)))
//    				break;
//    			List<String> nextEvents = this.activityEventDict.get(aNext);
//
//				if (!testedActivitiesTmp.contains(aNext)) {
//					testedActivitiesTmp.add(aNext);
//				}
//				if (!activityNumDictTmp.containsKey(aNext)) {
//					activityNumDictTmp.put(aNext, 0);
//				}
//				int activityNum = activityNumDictTmp.get(aNext);
//				activityNumDictTmp.replace(aNext, activityNum+1);
//				if (!(eventNumDictTmp.containsKey(event))) {
//					eventNumDictTmp.put(event, 0);
//				}
//				int eventNumTmp = eventNumDictTmp.get(event);
//				eventNumDictTmp.replace(event, eventNumTmp+1);
//
//				updateCounter(event, aNext, counterTmp);
//				updateProbModelByCounter(probModelTmp, counterTmp);
//    			gT += Math.pow(this.gamma, i) * this.getR(event, nextEvents, aNext, probModelTmp,
//						testedActivitiesTmp, activityNumDictTmp, eventNumDictTmp);
//				// Logger.println("***UTB*** update qTable, the value of gT" + gT.toString());
////    			event = this.getNextEvent(nextEvents);
//				event = this.selectEvent(nextEvents);
//				if (event == null){
//					break;
//				}
//    		}
//    	}
//		if (this.qTable.containsKey(event)){
//			gT += Math.pow(this.gamma, n) * this.qTable.get(event);
//		}
//    	return gT;
//    }

	public Double getQvalue(String event){
		if (qTable.containsKey(event))
			return qTable.get(event);
		else if((!probModel.containsKey(event))||(probModel.get(event).isEmpty()))
			return 0.0;
		else{
			String aNext = getNextActivity(probModel.get(event));
			if (!(this.activityEventDict.containsKey(aNext)))
    				return 0.0;
			Map<String, Map<String, Integer>> counterTmp = new HashMap<>(counter);
			Map<String, Map<String,Double>> probModelTmp = new HashMap<>(probModel);
			Map<String,Integer> activityNumDictTmp = new HashMap<>(activityNumDict);
			Map<String,Integer> eventNumDictTmp = new HashMap<>(eventNumDict);
			List<String> testedActivitiesTmp = new ArrayList<>(testedActivities);

			List<String> nextEvents = this.activityEventDict.get(aNext);
			if (!testedActivitiesTmp.contains(aNext)) {
				testedActivitiesTmp.add(aNext);
			}
			if (!activityNumDictTmp.containsKey(aNext)) {
				activityNumDictTmp.put(aNext, 0);
			}
			int activityNum = activityNumDictTmp.get(aNext);
			activityNumDictTmp.replace(aNext, activityNum+1);
			if (!(eventNumDictTmp.containsKey(event))) {
				eventNumDictTmp.put(event, 0);
			}
			int eventNumTmp = eventNumDictTmp.get(event);
			eventNumDictTmp.replace(event, eventNumTmp+1);

			updateCounter(event, aNext, counterTmp);
			updateProbModelByCounter(probModelTmp, counterTmp);

			return this.getR(event, nextEvents, aNext, probModelTmp,
						testedActivitiesTmp, activityNumDictTmp, eventNumDictTmp);
		}
	}

	public Double calNStepSarsa(double qValue) {
		double gT = 0.0;
		for (int i = 0; i < this.n; i++){
			gT += Math.pow(this.gamma, i) * rewardList.get(i);
		}
		gT += Math.pow(this.gamma, n) * qValue;
		return gT;
    }

	public void updateQTable(double qValue, double bonusReward) {
		String event = heventListForQ.get(0);
		if (!(this.qTable.containsKey(event))){
    		this.qTable.put(event, 0.0);
    	}
    	Double newValue = (1 - this.alpha) * this.qTable.get(event) + this.alpha * this.calNStepSarsa(qValue) +
				alpha * bonusReward;
    	this.qTable.replace(event, newValue);
		heventListForQ.remove(0);
		rewardList.remove(0);
		lastStateList.remove(0);
		nextStateList.remove(0);
		lastActList.remove(0);
		nextActList.remove(0);
    }
    
//    public void updateQTable(String event, String topComp, List<String> hstrList) {
//		if (!(this.qTable.containsKey(event))){
//    		this.qTable.put(event, 0.0);
//    	}
//    	Double newValue = (1 - this.alpha) * this.qTable.get(event) + this.alpha * this.calNStepSarsa(event, topComp, hstrList);
//    	this.qTable.replace(event, newValue);
//    }
    
	public String selectEvent(List<String> events) {
		double pqSum = 0.0;
		for(String e : events)
			if(this.qTable.containsKey(e)){
				pqSum += Math.exp(this.qTable.get(e) / this.beta);

			}
		Map<String,Double> pqDict = new HashMap<>();
		for(String e : events) {
			if(this.qTable.containsKey(e)) {
				pqDict.put(e, pqSum == 0.0 ? 0.0 : (Math.exp(this.qTable.get(e) / this.beta) / pqSum));
			}
		}
		//List<Map.Entry<String, Double>> listSort = new ArrayList<>(pqDict.entrySet());
		// Logger.dprintln(pqDict);
		String event = selectEventByProb(pqDict);
		/*for (String e : pqDict.keySet()) {
			if (pqDict.get(e) > pqDict.get(event)) {
				event = e;
			}
		}*/
//		Collections.sort(listSort, (o1, o2)->((int)(o2.getValue() - o1.getValue())));
//		HyperEvent event = listSort.get(0).getKey();
		return event;
	}
    
    public void updateCounter(String hevent, String act, Map<String, Map<String,Integer>> counter) {
    	if (counter.containsKey(hevent)) {
    		if (counter.get(hevent).containsKey(act)){
    			counter.get(hevent).replace(act, counter.get(hevent).get(act) + 1);
    		}
    		else {
    			counter.get(hevent).put(act, 1);
    		}
    	}
    	else {
    		Map<String,Integer> newCountMap = new HashMap<>();
    		newCountMap.put(act, 1);
    		counter.put(hevent, newCountMap);
    	}
    }
    
    public void updateProbModelByCounter(Map<String, Map<String,Double>> probModel, Map<String, Map<String,Integer>> counter) {
    	for (String hevent : counter.keySet()) {
    		// Calculate Sum
    		Integer allCounts = 0;
    		for (String act : counter.get(hevent).keySet()) {
    			allCounts += counter.get(hevent).get(act);
    		}
    		if (probModel.containsKey(hevent)) {
    			for (String act : counter.get(hevent).keySet()) {
    				probModel.get(hevent).put(act,
    						counter.get(hevent).get(act).doubleValue()/allCounts.doubleValue());
    			}
    		}
    		else {
    			Map<String,Double> newProbMap = new HashMap<>();
    			for (String act : counter.get(hevent).keySet()) {
    				newProbMap.put(act, 
    						counter.get(hevent).get(act).doubleValue()/allCounts.doubleValue());
    			}
    			probModel.put(hevent, newProbMap);
    		}
    	}
    }

    
    protected UtbState buildState(ComponentName topComp, GUITree gTree) {
    	return new UtbState(topComp, gTree);
    }
    
    public Boolean checkEventCoverage(List<HyperEvent> heventList, List<HyperEvent> notCoveredHevents) {
    	Boolean ret = true;
    	for (HyperEvent hevent : heventList) {
    		if (!this.probModel.containsKey(hevent.toString())) {
    			notCoveredHevents.add(hevent);
    			ret = false;
    		}

    	}
    	return ret;
    }
    
    public Boolean checkIsAllScrollActions(List<Action> acts) {
    	for (Action act : acts) {
    		if (!act.getType().isScroll()) {
    			return false;
    		}
    	}
    	return true;
    }
    
    public Boolean checkEventNum(List<String> hstrList) {
    	for (String e : hstrList) {
    		if ((!this.eventNumForExpDict.containsKey(e)) || 
    				(this.eventNumForExpDict.containsKey(e) && 
    						this.eventNumForExpDict.get(e) < this.numberUp)) {
    			return false;
    		}
    	}
    	return true;
    }

	public String selectEventByProb(Map<String,Double> eventProb) {
		double x = Math.random();
		double cumuliProb = 0.0;
		for (String item : eventProb.keySet()) {
			cumuliProb += eventProb.get(item);
			if (x <= cumuliProb)
				return item;
		}
		return null;
	}

	public Action selectActionByProb(Map<Action,Double> actionProb) {
		double x = Math.random();
		double cumuliProb = 0.0;
		for (Action item : actionProb.keySet()) {
			cumuliProb += actionProb.get(item);
			if (x <= cumuliProb)
				return item;
		}
		return null;
	}

	protected Action generateLoginAction(List<HyperEvent> heventList){
		HyperEvent h = null;
		for (HyperEvent hevent : heventList) {
			if(loginStep == 1){
				if (Objects.equals(hevent.getNode().getResourceID(), "cn.com.shbank.mper:id/login_et_password")) {
					h = hevent;
					break;
				}
			}else if(loginStep == loginTotalStep - 1){
				if (Objects.equals(hevent.getNode().getText(), "完成")){
					h = hevent;
					break;
				}
			}else if(loginStep == loginTotalStep){
				if (Objects.equals(hevent.getNode().getResourceID(), "cn.com.shbank.mper:id/login_btn_login")) {
					h = hevent;
					break;
				}
			}else{
				if (Objects.equals(hevent.getNode().getText(), loginInput.get(loginStep - 2))){
					h = hevent;
					break;
				}
			}
		}
		Action ar = new Action(ActionType.EVENT_NOP);
		if(loginStep == 2){
			System.out.print(ar);
			System.out.print(loginInput.get(loginStep - 2));
			System.out.print(h);
		}
		if (h != null) {
			Logger.format("***UTB*** Login Step: %s", loginStep);
			loginStep = loginStep % loginTotalStep + 1;
			for (Action a : h.getAllowedActions()) {
				if (a.getType() == ActionType.MODEL_CLICK) {
					ar = a;
					Logger.format("***UTB*** Selected Event: %s", h);
					Logger.format("***UTB*** Selected Action: %s", ar);
					break;
				}
			}
		} else{
//			System.out.print(ar);
			Logger.format("***UTB*** Login Step: %s", loginStep);
			Logger.format("***UTB*** Selected Event: waiting event");
			Logger.format("***UTB*** Selected Action: %s", ar);
//			System.out.print(loginInput.get(loginStep - 2));
//			System.out.print(heventList);
		}
		ar.setThrottle(400);
		return ar;
	}

	public GUITree buildGUITree(ComponentName activity, AccessibilityNodeInfo rootInfo, Bitmap bitmap) {
		GUITreeBuilder treeBuilder = new GUITreeBuilder(activity, rootInfo, bitmap);
		GUITree guiTree = treeBuilder.getGUITree();
		return guiTree;
	}

    // Core function to be modified. Choose the action.
    protected Action updateStateInternal(ComponentName topCompObj, AccessibilityNodeInfo info, boolean updateFlag) {
		// check activity and log in
    	newGUITree = buildGUITree(topCompObj, info, captureBitmap());
        newGUITree.setTimestamp(getTimestamp());
		String topComp = topCompObj.toString();
		utbState = buildState(topCompObj, newGUITree);
		// Avoid duplicated operations.
		List<Action> backActionList = new ArrayList<>();
		backActionList.add(utbState.backAction);
		HyperEvent backEvent = new HyperEvent(new GUITreeNode(null), topCompObj, backActionList);
		List<HyperEvent> heventList = utbState.getHyperEvents();
		if(heventList.size() == 0){
			Logger.format("***UTB*** hevent size is 0", topComp);
		}
		heventList.add(backEvent);
		List<String> hstrList = utbState.getHstrs();
		hstrList.add(backEvent.toString());
		Map<String, HyperEvent> hstrHeventMap = utbState.getStrEventMap();
		hstrHeventMap.put(backEvent.toString(), backEvent);
		this.activityEventDict.put(topComp, hstrList);
		// Logger.format("***UTB*** HyperEvent List: %s", heventList);
		// for no responding back action -- no need after adding black list
//		if (lastEvent!=null && lastEvent.getAllowedActions().contains(utbState.backAction) &&
//				lastEventList.size() == 1 && Objects.equals(utbState.getHstrs(), lastEventList)
//				&& checkActivityFlag){
//			this.checkActivityFlag = false;
//			return new Action(ActionType.EVENT_RESTART);
//		}
//		this.checkActivityFlag = true;
		if (!updateFlag){
			// start app for the first time
			if(this.heventsHistory.isEmpty()){
				if (!testedActivities.contains(topComp)) {
					testedActivities.add(topComp);
				}
				if (!this.activityNumDict.containsKey(topComp)) {
					this.activityNumDict.put(topComp, 0);
				}
				int activityNum = this.activityNumDict.get(topComp);
				this.activityNumDict.replace(topComp, activityNum+1);
			} else {
				//chose an event and executed it but led to other apps (instead of restarting app or logging in)
				if (this.lastEvent != null) {
					if (!(this.eventNumDict.containsKey(lastEvent.toString()))) {
						this.eventNumDict.put(lastEvent.toString(), 0);
					}
					int eventNumTmp = this.eventNumDict.get(lastEvent.toString());
					this.eventNumDict.replace(lastEvent.toString(), eventNumTmp + 1);

					updateCounter(this.lastEvent.toString(), topComp, this.counter);
					updateProbModelByCounter(this.probModel, this.counter);
//				updateQTable(this.lastEvent.toString(), topComp, hstrList);
					// update the reward and put it in the reward list
					// get the reward value for the last event

					heventListForQ.add(lastEvent.toString());
					double reward = getR(lastEvent.toString(), hstrList, topComp, probModel, testedActivities, activityNumDict, eventNumDict);
					rewardList.add(reward);
					lastStateList.add(this.lastEventList);
					nextStateList.add(hstrList);
					lastActList.add(this.lastActivity);
					nextActList.add(topComp);
				}
			}
		}
		if (updateFlag){
			// Update counter and probModel and qTable.
			if (this.lastEvent != null && (!this.heventsHistory.isEmpty()) && this.lastActivity != null) {
				if (!testedActivities.contains(topComp)) {
					testedActivities.add(topComp);
				}
				if (!this.activityNumDict.containsKey(topComp)) {
					this.activityNumDict.put(topComp, 0);
				}
				int activityNum = this.activityNumDict.get(topComp);
				this.activityNumDict.replace(topComp, activityNum+1);
				if (!(this.eventNumDict.containsKey(lastEvent.toString()))) {
					this.eventNumDict.put(lastEvent.toString(), 0);
				}
				int eventNumTmp = this.eventNumDict.get(lastEvent.toString());
				this.eventNumDict.replace(lastEvent.toString(), eventNumTmp+1);

				updateCounter(this.lastEvent.toString(), topComp, this.counter);
				updateProbModelByCounter(this.probModel, this.counter);
//				updateQTable(this.lastEvent.toString(), topComp, hstrList);
				// update the reward and put it in the reward list
				// get the reward value for the last event

				heventListForQ.add(lastEvent.toString());
				double reward = getR(lastEvent.toString(), hstrList, topComp, probModel, testedActivities, activityNumDict, eventNumDict);
				rewardList.add(reward);
				lastStateList.add(this.lastEventList);
				nextStateList.add(hstrList);
				lastActList.add(this.lastActivity);
				nextActList.add(topComp);
			}
		}

		// for step t: already taken A(t) and arrive at S(t+1)
		// get q value for Q(S(t+1), A(t+1)) before updating q table

		//saveGUI();
		if (Objects.equals(topCompObj.getClassName(), "com.yitong.mobile.biz.launcher.app.login.AuthBaseActivity$LoginByAllAuthActivity")) {
//			this.checkActivityFlag = false;
			this.lastEvent = null;
			this.lastEventList.clear();
			return generateLoginAction(heventList);
		}

        if (heventList.isEmpty() || 
        		(heventList.size() == 1 && checkIsAllScrollActions(heventList.get(0).getAllowedActions()))) {
        	this.lastEvent = null;
        	this.lastEventList.clear();
        	return utbState.backAction;
        }
        
        List<HyperEvent> notCoveredHevents = new ArrayList<>();
        HyperEvent selectedEvent = null;
		Boolean isExpansion = false;
        // Choose expansion or exploitation.
        if (!checkEventCoverage(heventList, notCoveredHevents)) {
        	// Expansion
        	Logger.println("***UTB*** Probabilty Model Expansion.");
        	int randNum = (int)(Math.random()*(notCoveredHevents.size()-1));
        	selectedEvent = notCoveredHevents.get(randNum);
			isExpansion = true;
        }
        else if (!(heventList.isEmpty()) && (qTable.keySet().containsAll(hstrList)) && checkEventNum(hstrList)) {
			Logger.println("***UTB*** select with qTable.");
        	selectedEvent = hstrHeventMap.get(this.selectEvent(hstrList));
//			Logger.format("***UTB*** qTable %s", qTable.toString());
        }
        else {
        	// Exploitation
        	Logger.println("***UTB*** Probabilty Model Exploitation.");
        	Map<String,Double> eMap = new HashMap<>();
        	Map<String,Double> pmMap = new HashMap<>();
        	Double alpha = 0.8;
        	Double sumPm = 0.0;
        	for (String hevent : hstrList){
        		if ((!this.eventNumForExpDict.containsKey(hevent)) ||
        				this.eventNumForExpDict.get(hevent) < this.numberUp) {
        			eMap.put(hevent, 0.0);
        	        if (this.probModel.containsKey(hevent)) {
        	        	for (String act : this.probModel.get(hevent).keySet()) {
        	        		if (!this.testedActivities.contains(act)) {
        	        			eMap.replace(hevent, eMap.get(hevent)+this.probModel.get(hevent).get(act));
        	        		}
        	        	}
        	        }
        	        pmMap.put(hevent, Math.exp(eMap.get(hevent)/alpha));
        	        sumPm += Math.exp(eMap.get(hevent)/alpha);
        		}
        	}
        	for (String hevent : pmMap.keySet()) {
        		pmMap.replace(hevent, pmMap.get(hevent)/sumPm);
        	}
			selectedEvent = hstrHeventMap.get(selectEventByProb(pmMap));
        	if (!this.eventNumForExpDict.containsKey(selectedEvent.toString())) {
        		this.eventNumForExpDict.put(selectedEvent.toString(), 1);
        	}
        	else {
        		this.eventNumForExpDict.replace(selectedEvent.toString(), 
        				this.eventNumForExpDict.get(selectedEvent.toString())+1);
        	}
        }
        
        Logger.format("***UTB*** Selected Event: %s", selectedEvent.toString());
        
        // Select action.
        Action selectedAction = utbState.backAction;
//        this.lastActivity = topCompObj;
        if (selectedEvent != null && !selectedEvent.getAllowedActions().isEmpty()) {
        	this.heventsHistory.add(selectedEvent);
        	this.lastEvent = selectedEvent;
        	this.lastEventList = utbState.getHstrs();
			this.lastActivity = topComp;

			// By weight
			Map<Action, Double> actWeightMap = new HashMap<>();
			int actValSum = 0;
			for (Action ma : selectedEvent.getAllowedActions()){
				actWeightMap.put(ma, 1.0);
			}
            if(!isExpansion){
				for (Action ma : selectedEvent.getAllowedActions()){
					if (ma.isScroll()){
						actWeightMap.put(ma, actWeightMap.get(ma) * 2);
					}
					else{
						actWeightMap.put(ma, actWeightMap.get(ma) / 2);
					}
					actValSum += actWeightMap.get(ma);
				}
				for (Action ma : actWeightMap.keySet()){
					actWeightMap.put(ma, actWeightMap.get(ma) / actValSum);
				}
				selectedAction = selectActionByProb(actWeightMap);
			}
			else{
				// Random
				int actionIndex = (int)(Math.random()*(selectedEvent.getAllowedActions().size()-1));
				selectedAction = selectedEvent.getAllowedActions().get(actionIndex);
			}

			// record the initial value of the events which are not ready for updating
			if (!this.qTable.containsKey(selectedEvent.toString())) {
				this.qTable.put(selectedEvent.toString(), 0.0);
			}
			// update q value for the first hevent in the list and then remove
			if(this.heventListForQ.size() == n){
				Double qValue = getQvalue(selectedEvent.toString());
				Double bonusReward = getBonusRewardByPBRS(this.gamma,
						this.lastStateList.get(0), this.lastActList.get(0),
						this.nextStateList.get(0), this.nextActList.get(0),
						this.probModel, this.testedActivities, this.activityNumDict, this.eventNumDict);
				updateQTable(qValue, bonusReward);
			}
			else{
				Logger.format("***UTB*** the length of heventListForQ: %s", heventListForQ.size());
				Logger.format("***UTB*** the length of rewardList: %s", rewardList.size());
				Logger.format("***UTB*** the length of lastStateList: %s", lastStateList.size());
				Logger.format("***UTB*** the length of nextStateList: %s", nextStateList.size());
				Logger.format("***UTB*** the length of lastActList: %s", lastActList.size());
				Logger.format("***UTB*** the length of nextActList: %s", nextActList.size());
			}
        }
        else {
        	this.lastEvent = null;
        	this.lastEventList.clear();
			this.lastActivity = null;
        }
        
        Logger.format("***UTB*** Selected Action: %s", selectedAction.toString());
        
        return selectedAction; // newAction are moved to currentAction in moveForward
    }

	@Override
	public int tearDown(File utbStateFile) {
//		ForJsonParse jsonParse = new ForJsonParse(this.activityEventDict,this.eventNumDict,
//				this.probModel, this.qTable);
//		PrintWriter utbStateWriter = openWriter(utbStateFile);
//		String jsonOutput = JSONObject.toJSONString(jsonParse);
//		JSONObject jsonObject = (JSONObject) JSONObject.toJSON(jsonParse);
//
//		//JSON对象转换为JSON字符串
//		String jsonOutput = jsonObject.toJSONString();
//		Gson gson = new Gson();

// Serialization
		try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(utbStateFile)))) {
//			out.write(gson.toJson(jsonParse));
//			out.println();
			Map<String, Object> map = new HashMap<>();
			map.put("activityEventDict", this.activityEventDict);
			map.put("eventNumDict", this.eventNumDict);
			map.put("probModel", this.probModel);
			map.put("qTable", this.qTable);
			JSONObject jo = new JSONObject(map);
			out.write(jo.toString());
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		Logger.format("Tested Activities: %s", testedActivities.toString());

		return this.testedActivities.size();
	}

	@Override
	public Action updateState(ComponentName topComp, AccessibilityNodeInfo info, boolean updateFlag) {
		// TODO Auto-generated method stub
		Action action = updateStateWrapper(topComp, info, updateFlag);
		return action;
	}

	private Action updateStateWrapper(ComponentName topComp, AccessibilityNodeInfo info, boolean updateFlag) {
		try {
			Logger.format(">>>>>>>> %s begin step [%d][Elapsed: %s]", getLoggerName(), ++timestamp,
					getElapsedTestingTime());
			printExploredActivity();
			printMemoryUsage();
			try {
				return updateStateInternal(topComp, info, updateFlag);
			} catch (BadStateException e) {
				Logger.wprintln("Bad state, retrieve the Window node.");
				info = ape.getRootInActiveWindowSlow();
				if (info == null) {
					Logger.wprintln("Fail to retrieve the Window node.");
					throw e;
				}
				return updateStateInternal(topComp, info, updateFlag);
			}
		} catch (BadStateException e) {
			Logger.wprintln("Handle bad state.");
			totalBadStates++;
			if (lastBadStateCount == (timestamp - 1)) {
				badStateCounter++;
			} else {
				badStateCounter = 0;
			}
			lastBadStateCount = timestamp;
			onBadState(lastBadStateCount, badStateCounter);
			if (badStateCounter > 10) {
				ape.stopTopActivity();
			}
			if (totalBadStates > 100) {
				throw new StopTestingException("Too many bad states.");
			}
			return handleBadState();
		} catch (Exception e) {
			throw e;
		} finally {
			Logger.format(">>>>>>>> %s end step [%d]", getLoggerName(), timestamp);
		}

	}


    public void onBadState(int lastBadStateCount, int badStateCounter) {
        logEvent(UtbEventType.BAD_STATE);
    }
	
	private void logEvent(UtbEventType badState) {
		// TODO Auto-generated method stub
		
	}
	
	protected Action handleBadState() {
        return Action.ACTIVATE;
    }

	@Override
	public void appendToActionHistory(long clockTimestamp, Action action) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean activityResuming(String arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean activityStarting(Intent arg0, String arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean appCrashed(String processName, int pid, String shortMsg, String longMsg, long timeMillis,
							  String stackTrace) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int appEarlyNotResponding(String arg0, int arg1, String arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int appNotResponding(String arg0, int arg1, String arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int systemNotResponding(String arg0) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void startNewEpisode() {
		// TODO Auto-generated method stub

	}

	@Override
	public int getTimestamp() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean canFuzzing() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Action generateFuzzingAction() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Rect getCurrentRootNodeBounds() {
		return null;
	}

	@Override
	public int nextInt(int bound) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void onAppActivityStarted(ComponentName app, boolean clean) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onActivityBlocked(ComponentName blockedActivity) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onActivityStopped() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onGraphStable(int counter) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onStateStable(int counter) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onVoidGUITree(int counter) {
		return false;
	}

	@Override
	public boolean onLostFocused(int counter) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void notifyActionConsumed() {
		GUITree.releaseLoadedData();
	}

	public String getLoggerName() {
		return "UTB";
	}

	public String getElapsedTestingTime() {
		long duration = System.currentTimeMillis() - beginMillis;
		long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;
		long minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60;
		long hours = TimeUnit.MILLISECONDS.toHours(duration) % 24;
		long days = TimeUnit.MILLISECONDS.toDays(duration);
		return String.format("%04d %02d:%02d:%02d", days, hours, minutes, seconds);
	}

	private void printExploredActivity() {
		if (timestamp % 50 == 0) {
			printActivities();
		} else {
			Logger.iformat("Explored %d app activities.", testedActivities.size());
		}
	}

	private void printActivities() {
		String[] names = this.activityNames.toArray(new String[0]);
		Arrays.sort(names);
		Logger.println("Explored app activities:");
		for (int i = 0; i < names.length; i++) {
			Logger.format("%4d %s", i + 1, names[i]);
		}
	}

	private void printMemoryUsage() {
		final Runtime runtime = Runtime.getRuntime();
		final long usedMemInMB=(runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
		final long maxHeapSizeInMB=runtime.maxMemory() / 1048576L;
		final long availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB;
		Logger.iformat("Used: %d MB, Max: %d MB, Available: %d MB", usedMemInMB, maxHeapSizeInMB, availHeapSizeInMB);
	}


	Bitmap captureBitmap() {
		return ape.captureBitmap();
	}

	protected File checkOutputDir() {
		File dir = ape.getOutputDirectory();
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return dir;
	}

	protected void saveGUI() {
		if (saveGUITreeToXmlEveryStep) {
			checkOutputDir();
			File xmlFile = new File(checkOutputDir(), String.format("step-%d.xml", getTimestamp()));
			Logger.iformat("Saving GUI tree to %s at step %d", xmlFile, getTimestamp());
			try {
				Utils.saveXml(xmlFile.getAbsolutePath(), newGUITree.getDocument());
			} catch (Exception e) {
				e.printStackTrace();
				Logger.wformat("Fail to save GUI tree to %s at step %d", xmlFile, getTimestamp());
			}
		}
		if (takeScreenshot && takeScreenshotForEveryStep) {
			checkOutputDir();
			File screenshotFile = new File(checkOutputDir(), String.format("step-%d.png", getTimestamp()));
			Logger.iformat("Saving screen shot to %s at step %d", screenshotFile, getTimestamp());
			ape.takeScreenshot(screenshotFile);
		}
	}
	
}

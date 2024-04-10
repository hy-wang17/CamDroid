package com.android.commands.monkey.ape.utils;

import android.content.ComponentName;

//import com.alibaba.fastjson.annotation.JSONField;

import java.util.List;
import java.util.Map;

public class ForJsonParse {
//    @JSONField(name = "activityEventDict")
    public Map<String, List<String>> activityEventDict;
//    @JSONField(name = "eventNumDict")
    public Map<String,Integer> eventNumDict;
//    @JSONField(name = "probModel")
    public Map<String, Map<String,Double>> probModel;
//    @JSONField(name = "qTable")
    public Map<String, Double> qTable;
    public ForJsonParse(){}
    public ForJsonParse(Map<String, List<String>> activityEventDict,
                        Map<String,Integer> eventNumDict,
                        Map<String, Map<String,Double>> probModel,
                        Map<String, Double> qTable){
        super();
        this.activityEventDict = activityEventDict;
        this.eventNumDict = eventNumDict; //put
        this.probModel = probModel; //put
        this.qTable = qTable;
    }

}

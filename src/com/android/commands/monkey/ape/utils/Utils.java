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
package com.android.commands.monkey.ape.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.graphics.PointF;

public class Utils {

    /**
     * 
     * @param jsonString
     * @return null if jsonString is invalid
     */
    public static JSONObject toJSON(String jsonString) {
        try {
            return new JSONObject(jsonString);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static JSONObject toJSON(PointF p) {
        try {
            JSONObject jObject = new JSONObject();
            jObject.put("x", p.x);
            jObject.put("y", p.y);
            return jObject;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static PointF parsePoint(JSONObject jObject) {
        try {
            float x = (float) jObject.getDouble("x");
            float y = (float) jObject.getDouble("y");
            return new PointF(x, y);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <V> Map<String, List<V>> parseMapKeyStringValueList(JSONObject keyValueListDict){
        Map<String, List<V>> result = new HashMap<>();
        for (Iterator<String> key = keyValueListDict.keys(); key.hasNext(); ) {
            String activity = key.next();
            JSONArray eventList = null;
            try {
                eventList = keyValueListDict.getJSONArray(activity);
                List<V> tmp = new ArrayList<>();
                for(int j = 0; j < eventList.length(); j++){
                    tmp.add((V) eventList.get(j));
                }
                result.put(activity, tmp);

            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }
        return result;
    }

    public static Map<String, Map<String, Double>> parseMapKeyStringValueMap(JSONObject keyValueDictDict){
        Map<String, Map<String, Double>> result = new HashMap<>();
        for (Iterator<String> it = keyValueDictDict.keys(); it.hasNext(); ) {
            String activity = it.next();
            JSONObject valueMap = null;
            try {
                valueMap = keyValueDictDict.getJSONObject(activity);
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
            if(valueMap == null)
                return null;
            Map<String, Double> tmp = new HashMap<>();
            try {
                for (Iterator<String> in = valueMap.keys(); in.hasNext(); ) {
                    String key = in.next();
                    double value = valueMap.getDouble(key);
                    tmp.put(key, value);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            result.put(activity, tmp);
        }
        return result;
    }

    public static Map<String, Integer> parseMapKeyStringValueInt(JSONObject keyValueDict){
        Map<String, Integer> result = new HashMap<>();
        try {
            for (Iterator<String> it = keyValueDict.keys(); it.hasNext(); ) {
                String key = it.next();
                int value = keyValueDict.getInt(key);
                result.put(key, value);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static Map<String, Double> parseMapKeyStringValueDouble(JSONObject keyValueDict){
        Map<String, Double> result = new HashMap<>();
        try {
            for (Iterator<String> it = keyValueDict.keys(); it.hasNext(); ) {
                String key = it.next();
                double value = keyValueDict.getDouble(key);
                result.put(key, value);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void reportConflict(Object expected, Object got) {
        Logger.wprintln("Expected: " + expected);
        Logger.wprintln("     Got: " + got);
    }

    public static <K, V> boolean addToMapSet(Map<K, Set<V>> mapSet, K key, V value) {
        Set<V> values = mapSet.get(key);
        if (values == null) {
            values = new HashSet<V>();
            mapSet.put(key, values);
        }
        return values.add(value);
    }

    public static String getProcessOutput(String [] cmd) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        StringBuilder processOutput = new StringBuilder();
        try (BufferedReader processOutputReader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));) {
            String readLine;
            while ((readLine = processOutputReader.readLine()) != null)
            {
                processOutput.append(readLine + System.lineSeparator());
            }
            process.waitFor();
        }
        return processOutput.toString().trim();
    }
    
    public static <V> Set<V> toSet(Iterator<V> iterator) {
        Set<V> set = new HashSet<V>();
        while (iterator.hasNext()) {
            set.add(iterator.next());
        }
        return set;
    }

    public static <V> List<V> addList(List<V> list, V v) {
        if (list == null) {
            return Collections.singletonList(v);
        } else if (list.size() == 1) {
            List<V> newList = new ArrayList<V>(2);
            newList.add(list.get(0));
            newList.add(v);
            return newList;
        }
        list.add(v);
        return list;
    }

    public static Document readXml(String filename) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(filename));
        return doc;
    }

    public static void dumpList(List<?> list) {
        if (list == null) {
            Logger.wprintln("Null list");
            return;
        }
        Iterator<?> it = list.iterator(); // many linked lists.
        for (int i = 0; i < list.size(); i++) {
            Logger.iformat("%3d %s", i, it.next());
        }
    }

    public static void dumpTree(Document tree) {
        dumpTree(tree.getDocumentElement());
    }

    public static void dumpTree(Element element) {
        dumpElement(element);
        NodeList nodeList = element.getChildNodes();
        int length = nodeList.getLength();
        for (int i = 0; i < length; i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element) {
                dumpTree((Element)node);
            }
        }
    }

    public static void dumpElement(Element element) {
        NamedNodeMap map = element.getAttributes();
        int length = map.getLength();
        Logger.iprintln("---------------------------------");
        Logger.iformat("> %s", element);
        for (int i = 0; i < length; i++) {
            Node item = map.item(i);
            Logger.iformat("> %s: %s [%s]", item.getNodeName(), item.getNodeValue(), item.getNodeType());
        }
        Logger.iprintln("---------------------------------");
    }

    public static void saveXml(String fileName, Document document) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            DOMSource source = new DOMSource(document);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            StreamResult result = new StreamResult(fos);
            transformer.transform(source, result);
        }
    }

    public static void printXml(OutputStream stream, Document document) throws Exception {
        DOMSource source = new DOMSource(document);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        StreamResult result = new StreamResult(stream);
        transformer.transform(source, result);
    }
    
    public static void saveString(String fileName, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            fos.write(content.getBytes());
        }
    }

    public static <K, V> void putAssertAbsent(Map<K, V> map, K key, V value) {
        if (map.put(key, value) != null) {
            throw new RuntimeException("Assertion failed! Duplicated key " + key);
        }
    }

    public static <K, V> boolean putIfAbsent(Map<K, V> map, K key, V value) {
        if (!map.containsKey(key)) {
            return map.put(key, value) == null;
        }
        return false;
    }

    public static void takeScreenshot(String fileName) throws Exception {
        Runtime.getRuntime().exec(new String[] { "screencap", "-p", fileName });
//        int exit = process.waitFor();
//        process.destroy();
    }

    public static <V> boolean addIfAbsent(Set<V> set, V value) {
        if (!set.contains(value)) {
            return set.add(value);
        }
        return false;
    }

    public static <K, V> void addToMapList(Map<K, List<V>> mapList, K key, V value) {
        List<V> values = mapList.get(key);
        if (values == null) {
            values = Collections.singletonList(value);
            mapList.put(key, values);
            return;
        }
        if (values.size() == 1) {
            V prev = values.get(0);
            values = new ArrayList<V>();
            values.add(prev);
            mapList.put(key, values);
        }
        values.add(value);
    }

    public static <K, K2, V> V getFromMapMap(Map<K, Map<K2, V>> mapMap, K key, K2 key2) {
        Map<K2, V> values = mapMap.get(key);
        if (values == null) {
            return null;
        }
        return values.get(key2);
    }

    public static <K, K2, V> V addToMapMap(Map<K, Map<K2, V>> mapMap, K key, K2 key2, V value) {
        Map<K2, V> values = mapMap.get(key);
        if (values == null) {
            values = new HashMap<K2, V>();
            mapMap.put(key, values);
        }
        return values.put(key2, value);
    }

    public static <K, K2, V> boolean addToMapMapIfAbsent(Map<K, Map<K2, V>> mapMap, K key, K2 key2, V value) {
        Map<K2, V> values = mapMap.get(key);
        if (values == null) {
            values = new HashMap<K2, V>();
            mapMap.put(key, values);
        }
        return putIfAbsent(values, key2, value);
    }

    public static <K, K2, V> V getFromMapMap(Map<K, Map<K2, V>> mapMap, K key, K2 key2, V defaultValue) {
        Map<K2, V> values = mapMap.get(key);
        if (values == null) {
            values = new HashMap<K2, V>();
            mapMap.put(key, values);
            values.put(key2, defaultValue);
            return defaultValue;
        }
        V value = values.get(key2);
        if (value == null) {
            values.put(key2, defaultValue);
            return defaultValue;
        }
        return value;
    }

    public static void assertNotNull(Object o) {
        if (o == null) {
            throw new NullPointerException();
        }
    }

    public static void assertNull(Object o) {
        if (o != null) {
            throw new IllegalArgumentException();
        }
    }

    public static <K, K2, V> Map<K2, V> getMapFromMap(Map<K, Map<K2, V>> mapMap, K key) {
        Map<K2, V> values = mapMap.get(key);
        if (values == null) {
            values = new HashMap<K2, V>();
            mapMap.put(key, values);
        }
        return values;
    }

    public static String join(String sep, String[] tokens) {
        if (tokens == null) {
            return "null";
        }
        if (tokens.length == 0) {
            return "";
        }
        if (tokens.length == 1) {
            return tokens[0];
        }
        StringBuilder sb = new StringBuilder();
        sb.append(tokens[0]);
        for (int i = 1; i < tokens.length; i++) {
            sb.append(sep);
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public static void dump(Object[] array) {
        for (int i = 0; i < array.length; i++) {
            Logger.iformat("%3d %s", i, array[i]);
        }
    }

    public static <K, K2, V> V removeFromMapMap(Map<K, Map<K2, V>> mapMap, K key, K2 key2) {
        Map<K2, V> values = mapMap.get(key);
        if (values == null) {
            return null;
        }
        return values.remove(key2);
    }

    public static <K, V> boolean containsMapSet(Map<K, Set<V>> mapSet, K key, V val) {
        Set<V> values = mapSet.get(key);
        if (values == null) {
            return false;
        }
        return values.contains(val);
    }

    public static <K, V> boolean removeFromMapSet(Map<K, Set<V>> mapSet, K key, V val) {
        Set<V> values = mapSet.get(key);
        if (values == null) {
            return false;
        }
        return values.remove(val);
    }

    public static <K1, K2, V> boolean containsMapMap(Map<K1, Map<K2, V>> mapMap, K1 key1, K2 key2) {
        Map<K2, V> map = mapMap.get(key1);
        if (map == null) {
            return false;
        }
        return map.containsKey(key2);
    }
    
	public static <V> List<V> intersect(List<V> l1, List<V> l2) {
		List<V> r = new ArrayList<>();
		for (V tmp : l1) {
			if (l2.contains(tmp)){
				r.add(tmp);
			}
		}
//		List<V> r = l1.stream().filter(item -> l2.contains(item)).collect(Collectors.toList());
		return r;
	}

    public static <V> List<V> removeDuplication(List<V> list) {
        HashSet<V> set = new HashSet<>(list);
        //把List集合所有元素清空
        list.clear();
        //把HashSet对象添加至List集合
        list.addAll(set);
        return list;
    }

	
//	public static <V> List<V> union(List<V> l1, List<V> l2) {
//		List<V> r1 = l1.parallelStream().collect(Collectors.toList());
//		List<V> r2 = l2.parallelStream().collect(Collectors.toList());
//		r1.addAll(r2);
//		r1 = r1.stream().distinct().collect(Collectors.toList());
//		return r1;
//	}
}

package com.changgeng.tool;

import java.util.*;
import java.util.stream.Collectors;

public class CommonTool {
    public static boolean isInteger(String str) {
        if (str == null) return false;
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // 简单杰卡德相似度
    private static List<String> simpleSegment(String text) {
        String[] words = text.replaceAll("[^a-zA-Z0-9\\s]", "").split("\\s+");
        return Arrays.asList(words);
    }

    private static double jaccardSimilarityWord(String str1, String str2) {
        Set<String> set1 = new HashSet<>(simpleSegment(str1));
        Set<String> set2 = new HashSet<>(simpleSegment(str2));

        int intersectionSize = (int) set1.stream().filter(set2::contains).count();
        int unionSize = set1.size() + set2.size() - intersectionSize;
        return (double) intersectionSize / unionSize;
    }

    // 字符级杰卡德相似度（去重后计算）
    public static double jaccardSimilarityChar(String str1, String str2) {
        Set<Character> set1 = new HashSet<>();
        Set<Character> set2 = new HashSet<>();

        for (char c : str1.toCharArray()) {
            set1.add(c);
        }
        for (char c : str2.toCharArray()) {
            set2.add(c);
        }

        int intersectionSize = 0;
        for (char c : set1)
            if (set2.contains(c)) intersectionSize++;
        int unionSize = set1.size() + set2.size() - intersectionSize;
        return (double) intersectionSize / unionSize;
    }

    // 杰卡德相似度
    public static double jaccardSimilarity(String str1, String str2) {
        Set<String> set1 = new HashSet<>();
        Set<String> set2 = new HashSet<>();
        for (int i = 0; i < str1.length(); i++) {
            set1.add(String.valueOf(str1.charAt(i)));
        }
        for (int i = 0; i < str2.length(); i++) {
            set2.add(String.valueOf(str2.charAt(i)));
        }
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        if (union.isEmpty()) {
            return 1.0;
        }
        return (double) intersection.size() / union.size();
    }

    // 混合相似度
    public static double mixedSimilarity(String str1, String str2) {
        double charSimilarity = jaccardSimilarityChar(str1, str2);
        double wordSimilarity = jaccardSimilarityWord(str1, str2);
        double oldSimilarity = jaccardSimilarity(str1, str2);
        return (charSimilarity + wordSimilarity + oldSimilarity) / 3.0;
    }

    /**
     * 优化后的混合相似度算法
     * @param entityStr 数据库中的实例/设备/测点名称 (短)
     * @param targetStr 用户输入的查询语句 (长)
     */
    public static double mixedSimilarity2(String entityStr, String targetStr) {
        if (entityStr == null || targetStr == null || entityStr.isEmpty() || targetStr.isEmpty()) {
            return 0.0;
        }
        if (targetStr.contains(entityStr)) {
            return 0.8 + 0.2 * ((double) entityStr.length() / targetStr.length());
        }
        return jaccardSimilarityChar(entityStr, targetStr);
    }

    // 获取最佳匹配字符串
    public static String getBestMatchingStr(List<String> strList, String targetStr) {
        double maxSimilarity = -1.0;
        String bestMatchingStr = null;
        for (String str : strList) {
            double similarity = mixedSimilarity(str, targetStr);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                bestMatchingStr = str;
            }
        }
        return bestMatchingStr;
    }

    // 获取前num个最佳匹配字符串
    public static List<Map> getBestMatchingStr(List<Map> mapList, String targetStr, int num) {
        if (mapList == null || mapList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map> scored = mapList.stream()
                .map(map -> {
                    String compareValue = map.get("name").toString();
                    double similarity = mixedSimilarity2(compareValue, targetStr);
                    Map result = new HashMap<>();
                    result.put("id", map.get("id"));
                    result.put("name", map.get("name"));
                    result.put("code", map.get("code"));
                    result.put("type", map.get("type"));
                    result.put("similarity", similarity);
                    return result;
                })
                .sorted((m1, m2) -> Double.compare(
                        (Double) m2.get("similarity"),
                        (Double) m1.get("similarity")
                ))
                .collect(Collectors.toList());

        if (num < 0) {
            return Collections.emptyList();
        }
        if (num == 0) {
            double maxSimilarity = (Double) scored.get(0).get("similarity");
            return scored.stream()
                    .filter(m -> Double.compare((Double) m.get("similarity"), maxSimilarity) == 0)
                    .collect(Collectors.toList());
        }
        return scored.subList(0, Math.min(num, scored.size()));
    }
}

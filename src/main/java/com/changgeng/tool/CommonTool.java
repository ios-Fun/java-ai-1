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
     * 三维评分：字符Dice(整体重叠) + 二元组Dice(顺序敏感) + LCS覆盖(连续匹配)
     * @param entityStr 数据库中的实例/设备/测点名称 (短)
     * @param targetStr 用户输入的查询语句 (长)
     */
    public static double mixedSimilarity2(String entityStr, String targetStr) {
        if (entityStr == null || targetStr == null || entityStr.isEmpty() || targetStr.isEmpty()) {
            return 0.0;
        }

        // 1. 字符级Dice系数：基于字符集合的重叠度
        Set<Character> entitySet = new HashSet<>();
        for (char c : entityStr.toCharArray()) entitySet.add(c);
        Set<Character> targetSet = new HashSet<>();
        for (char c : targetStr.toCharArray()) targetSet.add(c);

        int charIntersection = 0;
        for (char c : entitySet) {
            if (targetSet.contains(c)) charIntersection++;
        }
        if (charIntersection == 0) return 0.0;
        double charDice = 2.0 * charIntersection / (entitySet.size() + targetSet.size());

        // 2. 二元组Dice系数：捕捉字符顺序信息
        double bigramDice = 0.0;
        if (entityStr.length() >= 2) {
            Set<String> entityBigrams = new HashSet<>();
            for (int i = 0; i < entityStr.length() - 1; i++) {
                entityBigrams.add(entityStr.substring(i, i + 2));
            }
            Set<String> targetBigrams = new HashSet<>();
            for (int i = 0; i < targetStr.length() - 1; i++) {
                targetBigrams.add(targetStr.substring(i, i + 2));
            }
            int bigramIntersection = 0;
            for (String bg : entityBigrams) {
                if (targetBigrams.contains(bg)) bigramIntersection++;
            }
            bigramDice = 2.0 * bigramIntersection / (entityBigrams.size() + targetBigrams.size());
        }

        // 3. LCS覆盖奖励：最长公共连续子串占设备名的比例
        int lcsLen = longestCommonSubstringLen(entityStr, targetStr);
        double lcsCoverage = (double) lcsLen / entityStr.length();

        // 综合得分：字符Dice(40%) + 二元组Dice(40%) + LCS覆盖(20%)
        return Math.min(1.0, 0.4 * charDice + 0.4 * bigramDice + 0.2 * lcsCoverage);
    }

    //计算两个字符串的最长公共子串长度（动态规划）
    private static int longestCommonSubstringLen(String s1, String s2) {
        int maxLen = 0;
        int m = s1.length(), n = s2.length();
        // 优化：只用一行DP数组，空间O(n)
        int[] prev = new int[n + 1];
        for (int i = 1; i <= m; i++) {
            int[] curr = new int[n + 1];
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    curr[j] = prev[j - 1] + 1;
                    if (curr[j] > maxLen) maxLen = curr[j];
                }
            }
            prev = curr;
        }
        return maxLen;
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

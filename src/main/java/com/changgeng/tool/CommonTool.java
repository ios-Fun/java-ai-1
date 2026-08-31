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
     * 1. 完全一致独占 1.0
     * 2. 基于频次的字符统计（避免 Set 吃掉重复数字/编号）
     * 3. LCS 连续覆盖率主导
     * 4. 编号/后缀截断惩罚（精准区分 温度1、温度2 等测点）
     */
    public static double mixedSimilarity2(String entityStr, String targetStr) {
        if (entityStr == null || targetStr == null || entityStr.isEmpty() || targetStr.isEmpty()) {
            return 0.0;
        }

        // 1. 完全一致：满分 1.0
        if (entityStr.equals(targetStr)) {
            return 1.0;
        }

        int entityLen = entityStr.length();
        int targetLen = targetStr.length();

        // 2. 最长公共连续子串 (LCS)
        int lcsLen = longestCommonSubstringLen(entityStr, targetStr);
        if (lcsLen == 0) return 0.0;
        double entityCoverage = (double) lcsLen / entityLen;

        // 3. 基于频次的字符匹配率（区分多出现的编号数字）
        int[] targetCharCount = new int[65536];
        for (char c : targetStr.toCharArray()) targetCharCount[c]++;

        int commonCharCount = 0;
        for (char c : entityStr.toCharArray()) {
            if (targetCharCount[c] > 0) {
                commonCharCount++;
                targetCharCount[c]--;
            }
        }
        double charMatchRate = (double) commonCharCount / Math.max(entityLen, targetLen);

        // 4. 二元组Dice系数
        double bigramDice = 0.0;
        if (entityLen >= 2 && targetLen >= 2) {
            Map<String, Integer> entityBigrams = new HashMap<>();
            for (int i = 0; i < entityLen - 1; i++) {
                String bg = entityStr.substring(i, i + 2);
                entityBigrams.put(bg, entityBigrams.getOrDefault(bg, 0) + 1);
            }
            Map<String, Integer> targetBigrams = new HashMap<>();
            for (int i = 0; i < targetLen - 1; i++) {
                String bg = targetStr.substring(i, i + 2);
                targetBigrams.put(bg, targetBigrams.getOrDefault(bg, 0) + 1);
            }

            int bigramIntersect = 0;
            for (Map.Entry<String, Integer> entry : entityBigrams.entrySet()) {
                if (targetBigrams.containsKey(entry.getKey())) {
                    bigramIntersect += Math.min(entry.getValue(), targetBigrams.get(entry.getKey()));
                }
            }
            bigramDice = 2.0 * bigramIntersect / ((entityLen - 1) + (targetLen - 1));
        }

        // 5. 实体包含加成 与 精确编号后缀截断惩罚
        double containBonus = 0.0;
        double suffixPenalty = 0.0;
        int idx = targetStr.indexOf(entityStr);
        if (idx >= 0) {
            int endIdx = idx + entityLen;
            if (endIdx < targetLen) {
                char nextChar = targetStr.charAt(endIdx);
                // 关键修复：仅针对 ASCII 字母(A-Za-z)、数字(0-9)、#，不要用 Character.isLetter() 把汉字误伤
                boolean isCodeSuffix = (nextChar >= '0' && nextChar <= '9') ||
                        (nextChar >= 'a' && nextChar <= 'z') ||
                        (nextChar >= 'A' && nextChar <= 'Z') ||
                        (nextChar == '#');
                if (isCodeSuffix) {
                    suffixPenalty = 0.15; // 命中更短前缀，扣分
                } else {
                    containBonus = 0.15;  // 实体在句子中完整连续出现，加分
                }
            } else {
                containBonus = 0.15;
            }
        }

        // 综合得分计算
        double score = 0.50 * entityCoverage + 0.30 * bigramDice + 0.20 * charMatchRate + containBonus - suffixPenalty;

        return Math.max(0.0, Math.min(0.98, score));
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
    public static List<Map> getBestMatchingStr(List<Map> mapList, String targetStr, int num, String type) {
        if (mapList == null || mapList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map> scored = mapList.parallelStream()
                .filter(map -> type == null || type.isEmpty() || type.equals(map.get("type")) || map.get("type").toString().contains(type))
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

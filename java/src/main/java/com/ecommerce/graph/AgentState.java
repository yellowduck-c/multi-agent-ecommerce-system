package com.ecommerce.graph;

import java.util.List;
import java.util.Map;

/**
 * 定义 Graph 中流转的共享状态 Key
 * 对应原代码中的 request, profile, products 等参数
 */
public class AgentState {
    // 输入
    public static final String INPUT_USER_ID = "userId";
    public static final String INPUT_NUM_ITEMS = "numItems";

    // 中间数据
    public static final String KEY_USER_PROFILE = "userProfile";
    public static final String KEY_RAW_PRODUCTS = "rawProducts";
    public static final String KEY_RANKED_PRODUCTS = "rankedProducts";
    public static final String KEY_AVAILABLE_IDS = "availableIds";
    public static final String KEY_MARKETING_COPIES = "marketingCopies";

    // 输出
    public static final String OUTPUT_FINAL_PRODUCTS = "finalProducts";
    public static final String OUTPUT_EXPERIMENT_GROUP = "experimentGroup";
}
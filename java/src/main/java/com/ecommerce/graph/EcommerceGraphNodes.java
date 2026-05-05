package com.ecommerce.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.ecommerce.agent.*;
import com.ecommerce.model.Product;
import com.ecommerce.model.UserProfile;
import com.ecommerce.service.ABTestService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class EcommerceGraphNodes {

    private final UserProfileAgent userProfileAgent;
    private final ProductRecAgent productRecAgent;
    private final MarketingCopyAgent marketingCopyAgent;
    private final InventoryAgent inventoryAgent;
    private final ABTestService abTestService;

    // 构造函数注入保持不变...
    public EcommerceGraphNodes(UserProfileAgent userProfileAgent,
                               ProductRecAgent productRecAgent,
                               MarketingCopyAgent marketingCopyAgent,
                               InventoryAgent inventoryAgent,
                               ABTestService abTestService) {
        this.userProfileAgent = userProfileAgent;
        this.productRecAgent = productRecAgent;
        this.marketingCopyAgent = marketingCopyAgent;
        this.inventoryAgent = inventoryAgent;
        this.abTestService = abTestService;
    }

    /**
     * 节点 1: 用户画像 (同步)
     */
    public Map<String, Object> userProfileNode(OverAllState state) {
        String userId = (String) state.value(AgentState.INPUT_USER_ID).orElse(null);

        // 直接同步调用 Agent
        var result = userProfileAgent.run(Map.of("userId", userId));
        UserProfile profile = (UserProfile) result.getData().get("profile");
        String group = abTestService.assign(userId).getOrDefault("group", "control").toString();

        Map<String, Object> updates = new HashMap<>();
        updates.put(AgentState.KEY_USER_PROFILE, profile);
        updates.put(AgentState.OUTPUT_EXPERIMENT_GROUP, group);
        return updates;
    }

    /**
     * 节点 2: 商品召回 (同步)
     */
    public Map<String, Object> productRecallNode(OverAllState state) {
        int numItems = state.value(AgentState.INPUT_NUM_ITEMS)
                .map(val -> (Integer) val)
                .orElse(10);

        var result = productRecAgent.run(Map.of("numItems", numItems * 2));
        @SuppressWarnings("unchecked")
        List<Product> products = (List<Product>) result.getData().get("products");

        return Map.of(AgentState.KEY_RAW_PRODUCTS, products);
    }

    /**
     * 节点 3: 商品重排序 (同步)
     */
    public Map<String, Object> rerankNode(OverAllState state) {
        UserProfile profile = (UserProfile) state.value(AgentState.KEY_USER_PROFILE).orElse(null);
        int numItems = state.value(AgentState.INPUT_NUM_ITEMS).map(val -> (Integer) val).orElse(10);

        var result = productRecAgent.run(
                Map.of("userProfile", profile != null ? profile : new UserProfile(),
                        "numItems", numItems));

        @SuppressWarnings("unchecked")
        List<Product> ranked = (List<Product>) result.getData().get("products");
        return Map.of(AgentState.KEY_RANKED_PRODUCTS, ranked);
    }

    /**
     * 节点 4: 库存检查 (同步)
     */
    public Map<String, Object> inventoryNode(OverAllState state) {
        @SuppressWarnings("unchecked")
        List<Product> rawProducts = (List<Product>) state.value(AgentState.KEY_RAW_PRODUCTS).orElse(new ArrayList<>());

        var result = inventoryAgent.run(Map.of("products", rawProducts));
        @SuppressWarnings("unchecked")
        List<String> availableIds = (List<String>) result.getData().get("available_products");

        return Map.of(AgentState.KEY_AVAILABLE_IDS, availableIds);
    }

    /**
     * 节点 5: 营销文案生成 (同步)
     */
    public Map<String, Object> marketingNode(OverAllState state) {
        UserProfile profile = (UserProfile) state.value(AgentState.KEY_USER_PROFILE).orElse(null);
        @SuppressWarnings("unchecked")
        List<Product> finalProducts = (List<Product>) state.value(AgentState.OUTPUT_FINAL_PRODUCTS).orElse(new ArrayList<>());

        var result = marketingCopyAgent.run(
                Map.of("userProfile", profile != null ? profile : new UserProfile(),
                        "products", finalProducts));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> copies = (List<Map<String, String>>) result.getData().get("copies");

        return Map.of(AgentState.KEY_MARKETING_COPIES, copies);
    }

    /**
     * 节点 6: 最终聚合逻辑 (同步)
     */
    public Map<String, Object> aggregatorNode(OverAllState state) {
        int numItems = state.value(AgentState.INPUT_NUM_ITEMS).map(val -> (Integer) val).orElse(10);
        @SuppressWarnings("unchecked")
        List<Product> rankedProducts = (List<Product>) state.value(AgentState.KEY_RANKED_PRODUCTS).orElse(new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<String> availableIds = (List<String>) state.value(AgentState.KEY_AVAILABLE_IDS).orElse(new ArrayList<>());

        Set<String> availSet = new HashSet<>(availableIds);

        List<Product> finalProducts = rankedProducts.stream()
                .filter(p -> availSet.contains(p.getProductId()))
                .limit(numItems)
                .collect(Collectors.toList());

        if (finalProducts.isEmpty()) {
            finalProducts = rankedProducts.stream().limit(numItems).collect(Collectors.toList());
        }

        return Map.of(AgentState.OUTPUT_FINAL_PRODUCTS, finalProducts);
    }
}
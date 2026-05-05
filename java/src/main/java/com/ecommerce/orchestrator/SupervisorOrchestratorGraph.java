package com.ecommerce.orchestrator;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.ecommerce.model.*;
import com.ecommerce.graph.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupervisorOrchestratorGraph {

    private static final Logger log = LoggerFactory.getLogger(SupervisorOrchestratorGraph.class);

    private final CompiledGraph compiledGraph;

    public SupervisorOrchestratorGraph(CompiledGraph compiledGraph) {
        this.compiledGraph = compiledGraph;
    }

    public RecommendationResponse recommend(RecommendationRequest request) {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();

        log.info("[Supervisor] start request={} user={}", requestId, request.getUserId());

        // 1. 构建初始状态
        Map<String, Object> inputState = new HashMap<>();
        inputState.put(AgentState.INPUT_USER_ID, request.getUserId());
        inputState.put(AgentState.INPUT_NUM_ITEMS, request.getNumItems());

        // 2. 运行图 - invoke 返回 Optional<OverAllState>
        OverAllState finalState = compiledGraph.invoke(inputState)
                .orElseThrow(() -> new RuntimeException("Graph execution returned empty result"));

        // 3. 从 OverAllState 中提取结果（使用 value() 方法）
        @SuppressWarnings("unchecked")
        List<Product> finalProducts = (List<Product>) finalState
                .value(AgentState.OUTPUT_FINAL_PRODUCTS)
                .orElse(List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, String>> copies = (List<Map<String, String>>) finalState
                .value(AgentState.KEY_MARKETING_COPIES)
                .orElse(List.of());

        String experimentGroup = (String) finalState
                .value(AgentState.OUTPUT_EXPERIMENT_GROUP)
                .orElse("control");

        double totalLatency = (System.nanoTime() - start) / 1_000_000.0;
        log.info("[Supervisor] complete request={} latency={:.1f}ms products={}",
                requestId, totalLatency, finalProducts.size());

        return RecommendationResponse.builder()
                .requestId(requestId)
                .userId(request.getUserId())
                .products(finalProducts)
                .marketingCopies(copies)
                .experimentGroup(experimentGroup)
                .totalLatencyMs(totalLatency)
                .build();
    }
}
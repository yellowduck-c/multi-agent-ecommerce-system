package com.ecommerce.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

@Configuration
public class EcommerceGraphBuilder {

    private final EcommerceGraphNodes nodes;

    public EcommerceGraphBuilder(EcommerceGraphNodes nodes) {
        this.nodes = nodes;
    }

    @Bean
    public StateGraph ecommerceStateGraph() throws GraphStateException {
        StateGraph graph = new StateGraph();

        // --- 1. 定义入口节点 ---
        // 直接在 entry 节点做简单的初始化，或者如果不需要逻辑，START 可以直接连业务节点
        graph.addNode("entry", AsyncNodeAction.node_async(state -> {
            System.out.println(">>> [Entry] 流程开始");
            return Map.of("init", "success");
        }));

        // --- 2. 定义业务节点 ---
        graph.addNode("user_profile", AsyncNodeAction.node_async(nodes::userProfileNode));
        graph.addNode("product_recall", AsyncNodeAction.node_async(nodes::productRecallNode));
        graph.addNode("inventory", AsyncNodeAction.node_async(nodes::inventoryNode));
        graph.addNode("aggregator", AsyncNodeAction.node_async(nodes::aggregatorNode));

        // --- 3. 定义边 (直接并行) ---

        // 1. 开始 -> 入口
        graph.addEdge(START, "entry");

        // 2. 入口 -> 并行分支 (直接连接三个节点)
        // 框架检测到 "entry" 有三个下游，会自动并行执行它们
        graph.addEdge("entry", "user_profile");
        graph.addEdge("entry", "product_recall");
        graph.addEdge("entry", "inventory");

        // 3. 并行分支 -> 汇聚
        graph.addEdge("user_profile", "aggregator");
        graph.addEdge("product_recall", "aggregator");
        graph.addEdge("inventory", "aggregator");

        // 4. 结束
        graph.addEdge("aggregator", END);

        return graph;
    }

    @Bean
    public CompiledGraph compiledGraph(StateGraph ecommerceStateGraph) throws GraphStateException {
        return ecommerceStateGraph.compile();
    }
}
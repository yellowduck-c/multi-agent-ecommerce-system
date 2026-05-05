package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基础代理类，包含重试、超时、降级（fallback）和指标统计功能。
 * 所有四个领域的代理类都继承自此类。
 */
public abstract class BaseAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final String name;
    protected final double timeoutSeconds;
    protected final int maxRetries;

    // 用于统计调用次数的原子计数器
    private final AtomicInteger callCount = new AtomicInteger(0);
    // 用于统计错误次数的原子计数器
    private final AtomicInteger errorCount = new AtomicInteger(0);

    protected BaseAgent(String name, double timeoutSeconds, int maxRetries) {
        this.name = name;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
    }

    /**
     * 同步执行方法。
     * 包含核心业务逻辑：重试机制、指标记录、延迟计算和降级处理。
     *
     * @param params 执行参数
     * @return 代理执行结果
     */
    public AgentResult run(Map<String, Object> params) {
        // 增加调用计数
        callCount.incrementAndGet();
        long start = System.nanoTime();
        int attempt = 0;
        Exception lastError = null;

        // 重试循环
        while (attempt < maxRetries) {
            try {
                // 执行具体的业务逻辑（由子类实现）
                AgentResult result = execute(params);
                double latency = (System.nanoTime() - start) / 1_000_000.0;
                result.setLatencyMs(latency);
                log.info("[{}] 成功，耗时 {:.1f}ms", name, latency);
                return result;
            } catch (Exception e) {
                lastError = e;
                attempt++;
                log.warn("[{}] 第 {} 次尝试失败: {}", name, attempt, e.getMessage());

                // 如果还没达到最大重试次数，进行指数退避等待
                if (attempt < maxRetries) {
                    try {
                        // 退避时间：500ms * 2^(attempt-1)
                        Thread.sleep((long) (500 * Math.pow(2, attempt - 1)));
                    } catch (InterruptedException ie) {
                        // 如果等待期间被中断，恢复中断状态并退出循环
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 所有重试均失败，增加错误计数
        errorCount.incrementAndGet();
        double latency = (System.nanoTime() - start) / 1_000_000.0;
        // 执行降级逻辑
        return fallback(latency, lastError);
    }

    /**
     * 异步执行方法。
     * 直接包装同步 run 方法，利用 CompletableFuture 在后台线程池执行。
     *
     * @param params 执行参数
     * @return 包含未来结果的 CompletableFuture
     */
    public CompletableFuture<AgentResult> runAsync(Map<String, Object> params) {
        // 异步调用同步方法
        return CompletableFuture.supplyAsync(() -> run(params));
    }

    /**
     * 抽象方法：子类必须实现具体的执行逻辑。
     *
     * @param params 执行参数
     * @return 代理执行结果
     * @throws Exception 执行过程中可能抛出的异常
     */
    protected abstract AgentResult execute(Map<String, Object> params) throws Exception;

    /**
     * 降级方法：当所有重试都失败时调用。
     *
     * @param latencyMs 耗时
     * @param e 最后的异常
     * @return 降级的默认结果
     */
    protected AgentResult fallback(double latencyMs, Exception e) {
        return AgentResult.builder()
                .agentName(name)
                .success(false)
                .latencyMs(latencyMs)
                .error(e != null ? e.getMessage() : "未知错误")
                .confidence(0.0)
                .build();
    }

    /**
     * 获取当前的错误率 (错误次数 / 总调用次数)。
     *
     * @return 错误率
     */
    public double getErrorRate() {
        int calls = callCount.get();
        return calls == 0 ? 0.0 : (double) errorCount.get() / calls;
    }
}
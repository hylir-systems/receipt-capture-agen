package com.hylir.receipt.controller;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 状态更新管理器
 * 负责节流和批量处理高频 UI 更新，避免 Platform.runLater 任务堆积
 *
 * @author shanghai pubing
 * @date 2025/01/26
 */
public class StatusUpdateManager {

    private static final Logger logger = LoggerFactory.getLogger(StatusUpdateManager.class);

    private final TextArea statusArea;
    private final Label statusLabel;

    // 状态消息队列（线程安全）
    private final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();

    // 批量更新标志
    private final AtomicBoolean isUpdating = new AtomicBoolean(false);

    // 使用 Timeline 进行节流更新（每 300ms 更新一次）
    private Timeline updateTimeline;

    // 高频更新计数器（用于批量显示）
    private int highFrequencyUpdateCount = 0;
    private String lastHighFrequencyMessage = "";

    // 更新间隔（毫秒）
    private static final long UPDATE_INTERVAL_MS = 300;
    private static final int MAX_BATCH_SIZE = 10; // 最多批量显示 10 条消息

    public StatusUpdateManager(TextArea statusArea, Label statusLabel) {
        this.statusArea = statusArea;
        this.statusLabel = statusLabel;
        initializeTimeline();
    }

    /**
     * 初始化 Timeline 用于定期更新 UI
     */
    private void initializeTimeline() {
        updateTimeline = new Timeline(
                new KeyFrame(Duration.millis(UPDATE_INTERVAL_MS), e -> flushMessages())
        );
        updateTimeline.setCycleCount(Timeline.INDEFINITE);
        updateTimeline.play();
    }

    /**
     * 添加状态消息（线程安全，非阻塞）
     * 消息会被加入队列，由 Timeline 定期批量更新
     *
     * @param message 状态消息
     */
    public void appendStatus(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        // 对于高频消息（如帧延迟），进行去重和计数
        if (isHighFrequencyMessage(message)) {
            handleHighFrequencyMessage(message);
        } else {
            // 普通消息直接加入队列
            messageQueue.offer(message);
        }

        // 触发立即更新（如果队列积压过多）
        if (messageQueue.size() > MAX_BATCH_SIZE) {
            Platform.runLater(this::flushMessages);
        }
    }

    /**
     * 判断是否为高频消息
     */
    private boolean isHighFrequencyMessage(String message) {
        return message.contains("延迟") || 
               message.contains("帧率") || 
               message.contains("FPS") ||
               message.contains("latency");
    }

    /**
     * 处理高频消息（去重和计数）
     */
    private void handleHighFrequencyMessage(String message) {
        if (message.equals(lastHighFrequencyMessage)) {
            highFrequencyUpdateCount++;
        } else {
            // 如果之前有高频消息，先输出汇总
            if (highFrequencyUpdateCount > 0 && !lastHighFrequencyMessage.isEmpty()) {
                String summary = lastHighFrequencyMessage;
                if (highFrequencyUpdateCount > 1) {
                    summary += " (更新 " + highFrequencyUpdateCount + " 次)";
                }
                messageQueue.offer(summary);
            }
            lastHighFrequencyMessage = message;
            highFrequencyUpdateCount = 1;
        }
    }

    /**
     * 批量刷新消息到 UI（Timeline 已在 JavaFX 应用线程中执行，无需 Platform.runLater）
     */
    private void flushMessages() {
        if (isUpdating.get()) {
            return; // 防止并发更新
        }

        if (messageQueue.isEmpty() && highFrequencyUpdateCount == 0) {
            return; // 没有消息需要更新
        }

        // Timeline 的回调已经在 JavaFX 应用线程中执行，无需 Platform.runLater
        if (!isUpdating.compareAndSet(false, true)) {
            return;
        }

        try {
            int batchCount = 0;
            String lastStatusMessage = null;

            // 批量处理队列中的消息
            while (!messageQueue.isEmpty() && batchCount < MAX_BATCH_SIZE) {
                String message = messageQueue.poll();
                if (message != null) {
                    appendToStatusArea(message);
                    lastStatusMessage = message;
                    batchCount++;
                }
            }

            // 处理高频消息汇总
            if (highFrequencyUpdateCount > 0 && !lastHighFrequencyMessage.isEmpty()) {
                String summary = lastHighFrequencyMessage;
                if (highFrequencyUpdateCount > 1) {
                    summary += " (更新 " + highFrequencyUpdateCount + " 次)";
                }
                appendToStatusArea(summary);
                lastStatusMessage = summary;
                highFrequencyUpdateCount = 0;
                lastHighFrequencyMessage = "";
            }

            // 更新状态标签（使用最后一条消息）
            if (lastStatusMessage != null) {
                updateStatusLabel(lastStatusMessage);
            }

        } catch (Exception e) {
            logger.error("刷新状态消息失败", e);
        } finally {
            isUpdating.set(false);
        }
    }

    /**
     * 追加消息到状态区域
     */
    private void appendToStatusArea(String message) {
        if (statusArea != null) {
            String timestamp = "[" + java.time.LocalTime.now().toString() + "] ";
            statusArea.appendText(timestamp + message + "\n");

            // 限制日志区域最大行数（保留最近 1000 行）
            int maxLines = 1000;
            if (statusArea.getParagraphs().size() > maxLines) {
                int removeCount = statusArea.getParagraphs().size() - maxLines;
                statusArea.deleteText(0, statusArea.getText().indexOf('\n', 
                    statusArea.getText().length() / removeCount) + 1);
            }
        }
    }

    /**
     * 更新状态标签
     */
    private void updateStatusLabel(String message) {
        if (statusLabel == null) {
            return;
        }

        // 根据消息内容更新状态标签
        if (message.contains("✓") || message.contains("成功")) {
            statusLabel.setText("成功");
        } else if (message.contains("✗") || message.contains("失败")) {
            statusLabel.setText("错误");
        } else if (message.contains("正在") || message.contains("处理")) {
            statusLabel.setText("处理中");
        } else if (message.contains("预览")) {
            statusLabel.setText("预览中");
        }
        // 其他情况不更新，保持当前状态
    }

    /**
     * 立即刷新所有待处理的消息（用于重要消息）
     */
    public void flushImmediately() {
        Platform.runLater(this::flushMessages);
    }

    /**
     * 清空消息队列
     */
    public void clear() {
        messageQueue.clear();
        highFrequencyUpdateCount = 0;
        lastHighFrequencyMessage = "";
    }

    /**
     * 停止更新管理器
     */
    public void shutdown() {
        if (updateTimeline != null) {
            updateTimeline.stop();
        }
        clear();
    }
}


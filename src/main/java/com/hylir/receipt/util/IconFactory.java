package com.hylir.receipt.util;

import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * 现代化图标工厂 - 使用 Ikonli 图标库
 * 适合回单采集终端 UI 风格
 *
 * Author: shanghai pubing
 * Date: 2026/01/27
 */
public class IconFactory {

    private static final Color PRIMARY_COLOR = Color.web("#127cd8");
    private static final Color SUCCESS_COLOR = Color.web("#8DC21F");
    private static final Color ERROR_COLOR = Color.web("#f3002f");
    private static final Color WARNING_COLOR = Color.web("#f39c12");
    private static final Color BG_COLOR = Color.web("#f5f6fa");
    private static final Color WHITE = Color.WHITE;

    private static DropShadow iconShadow() {
        return new DropShadow(3, 2, 2, Color.rgb(0, 0, 0, 0.15));
    }

    /**
     * 创建带背景的图标
     */
    private static Node createIconWithBackground(Ikon iconCode, double size, Color iconColor) {
        StackPane icon = new StackPane();
        icon.setPrefSize(size, size);

        // 背景圆
        Circle bg = new Circle(size * 0.5, BG_COLOR);
        bg.setEffect(iconShadow());

        // Ikonli 图标
        FontIcon iconNode = new FontIcon(iconCode);
        iconNode.setIconSize((int)(size * 0.5));
        iconNode.setIconColor(iconColor);

        icon.getChildren().addAll(bg, iconNode);
        return icon;
    }

    /**
     * 文档图标
     */
    public static Node createDocumentIcon(double size) {
        return createIconWithBackground(FontAwesomeSolid.FILE_ALT, size, PRIMARY_COLOR);
    }

    /**
     * 摄像头图标
     */
    public static Node createCameraIcon(double size) {
        return createIconWithBackground(FontAwesomeSolid.CAMERA, size, PRIMARY_COLOR);
    }

    /**
     * 播放图标
     */
    public static Node createPlayIcon(double size) {
        return createIconWithBackground(FontAwesomeSolid.PLAY, size, PRIMARY_COLOR);
    }

    /**
     * 重置图标
     */
    public static Node createResetIcon(double size) {
        return createIconWithBackground(FontAwesomeSolid.REDO, size, PRIMARY_COLOR);
    }

    /**
     * 设置图标
     */
    public static Node createSettingsIcon(double size) {
        return createIconWithBackground(FontAwesomeSolid.COG, size, PRIMARY_COLOR);
    }

    /**
     * 日志图标
     */
    public static Node createLogIcon(double size) {
        return createIconWithBackground(FontAwesomeSolid.SCROLL, size, PRIMARY_COLOR);
    }

    /**
     * 列表图标
     */
    public static Node createListIcon(double size) {
        return createIconWithBackground(FontAwesomeSolid.LIST, size, PRIMARY_COLOR);
    }
}

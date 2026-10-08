package com.sentinelav.desktop;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Smooth hover feedback for every card, stat card and tile: the element grows slightly, lifts and
 * gains a teal glow. Detection happens at scene level so it also works for nodes inside scroll panes.
 */
final class HoverLift {
    private static final String KEY = "hover-lift";
    private static final Color GLOW = Color.web("#2dd4bf");

    private final DropShadow glow = new DropShadow(0, 0, 6, new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), 0));
    private final Timeline timeline = new Timeline();
    private final Node node;

    private HoverLift(Node node) {
        this.node = node;
        glow.setSpread(0.02);
    }

    static void install(Scene scene) {
        Node[] current = new Node[1];
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            Node target = e.getTarget() instanceof Node n ? hoverable(n) : null;
            if (target != current[0]) {
                if (current[0] != null && containsPoint(current[0], e.getSceneX(), e.getSceneY())) {
                    return;
                }
                leave(current[0]);
                current[0] = target;
                enter(target);
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
            leave(current[0]);
            current[0] = null;
        });
    }

    // Hit test on the untransformed layout rectangle so the lift itself cannot cause flicker at the edges.
    private static boolean containsPoint(Node node, double sceneX, double sceneY) {
        if (node.getParent() == null) {
            return false;
        }
        var p = node.getParent().sceneToLocal(sceneX, sceneY);
        if (p == null) {
            return false;
        }
        var b = node.getLayoutBounds();
        double x = p.getX() - node.getLayoutX();
        double y = p.getY() - node.getLayoutY();
        return x >= b.getMinX() && x <= b.getMaxX() && y >= b.getMinY() && y <= b.getMaxY();
    }

    private static Node hoverable(Node start) {
        for (Node n = start; n != null; n = n.getParent()) {
            var classes = n.getStyleClass();
            if (classes.contains("tile") || classes.contains("stat-card")) {
                return n;
            }
            if (classes.contains("card") || classes.contains("hero")) {
                return containsTile(n) ? null : n;
            }
        }
        return null;
    }

    private static boolean containsTile(Node container) {
        return container instanceof javafx.scene.Parent p
                && !p.lookupAll(".tile").isEmpty() && p.lookupAll(".tile").stream().anyMatch(t -> t != p);
    }


    private static HoverLift of(Node node) {
        HoverLift lift = (HoverLift) node.getProperties().get(KEY);
        if (lift == null) {
            lift = new HoverLift(node);
            node.getProperties().put(KEY, lift);
        }
        return lift;
    }

    private static void enter(Node node) {
        if (node == null || node.isDisabled()) {
            return;
        }
        HoverLift lift = of(node);
        double width = Math.max(1, node.getLayoutBounds().getWidth());
        double scale = 1 + Math.min(0.045, 16 / width);
        node.setViewOrder(-1);
        node.setEffect(lift.glow);
        lift.play(-5, scale, 30, 0.40);
    }

    private static void leave(Node node) {
        if (node == null) {
            return;
        }
        HoverLift lift = of(node);
        lift.play(0, 1.0, 0, 0);
        lift.timeline.setOnFinished(ev -> {
            if (!node.isHover()) {
                node.setEffect(null);
                node.setViewOrder(0);
            }
        });
    }

    private void play(double y, double scale, double radius, double opacity) {
        timeline.stop();
        timeline.setOnFinished(null);
        timeline.getKeyFrames().setAll(new KeyFrame(Duration.millis(180),
                new KeyValue(node.translateYProperty(), y, Interpolator.EASE_OUT),
                new KeyValue(node.scaleXProperty(), scale, Interpolator.EASE_OUT),
                new KeyValue(node.scaleYProperty(), scale, Interpolator.EASE_OUT),
                new KeyValue(glow.radiusProperty(), radius, Interpolator.EASE_OUT),
                new KeyValue(glow.colorProperty(),
                        new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), opacity),
                        Interpolator.EASE_OUT)));
        timeline.play();
    }
}

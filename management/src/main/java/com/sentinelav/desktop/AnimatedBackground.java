package com.sentinelav.desktop;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;

import java.util.Random;

/** Subtle animated backdrop: drifting colour glows, a faint grid and a slow particle network. */
final class AnimatedBackground extends Pane {
    private static final char[] HEX = "0123456789ABCDEF01".toCharArray();
    private static final int PARTICLES = 46;
    private static final double LINK_DISTANCE = 130;
    private static final long FRAME_NANOS = 33_000_000L;

    private final Canvas canvas = new Canvas();
    private final double[] px = new double[PARTICLES];
    private final double[] py = new double[PARTICLES];
    private final double[] vx = new double[PARTICLES];
    private final double[] vy = new double[PARTICLES];
    private final AnimationTimer timer;
    private long last;
    private double time;

    AnimatedBackground() {
        setMouseTransparent(true);
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        Random rnd = new Random(7);
        for (int i = 0; i < PARTICLES; i++) {
            px[i] = rnd.nextDouble();
            py[i] = rnd.nextDouble();
            vx[i] = (rnd.nextDouble() - 0.5) * 0.012;
            vy[i] = (rnd.nextDouble() - 0.5) * 0.012;
        }
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - last < FRAME_NANOS) {
                    return;
                }
                double dt = last == 0 ? 0.033 : (now - last) / 1e9;
                last = now;
                time += dt;
                draw(dt);
            }
        };
        sceneProperty().addListener((o, a, scene) -> {
            timer.stop();
            if (scene != null) {
                scene.windowProperty().addListener((o2, w0, window) -> {
                    if (window != null) {
                        window.showingProperty().addListener((o3, s0, showing) -> toggle(showing));
                        window.focusedProperty().addListener((o3, f0, focused) -> toggle(focused));
                    }
                });
                if (scene.getWindow() != null) {
                    toggle(scene.getWindow().isShowing());
                } else {
                    timer.start();
                }
            }
        });
    }

    private void toggle(boolean run) {
        if (run) {
            last = 0;
            timer.start();
        } else {
            timer.stop();
        }
    }

    private void draw(double dt) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w < 2 || h < 2) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#0a0e13"));
        g.fillRect(0, 0, w, h);

        glow(g, w * (0.30 + 0.18 * Math.sin(time * 0.11)), h * (0.25 + 0.15 * Math.cos(time * 0.09)),
                Math.max(w, h) * 0.55, Color.web("#2dd4bf", 0.10));
        glow(g, w * (0.80 + 0.12 * Math.cos(time * 0.08)), h * (0.70 + 0.14 * Math.sin(time * 0.12)),
                Math.max(w, h) * 0.60, Color.web("#60a5fa", 0.09));
        glow(g, w * (0.55 + 0.20 * Math.sin(time * 0.07 + 2)), h * (0.10 + 0.10 * Math.cos(time * 0.1)),
                Math.max(w, h) * 0.40, Color.web("#8b5cf6", 0.05));

        drawHexGrid(g, w, h);
        drawDataRain(g, w, h, dt);
        drawRadar(g, w, h);

        for (int i = 0; i < PARTICLES; i++) {
            px[i] += vx[i] * dt * 6;
            py[i] += vy[i] * dt * 6;
            if (px[i] < 0 || px[i] > 1) {
                vx[i] = -vx[i];
                px[i] = Math.max(0, Math.min(1, px[i]));
            }
            if (py[i] < 0 || py[i] > 1) {
                vy[i] = -vy[i];
                py[i] = Math.max(0, Math.min(1, py[i]));
            }
        }
        for (int i = 0; i < PARTICLES; i++) {
            double x1 = px[i] * w;
            double y1 = py[i] * h;
            for (int j = i + 1; j < PARTICLES; j++) {
                double dx = x1 - px[j] * w;
                double dy = y1 - py[j] * h;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < LINK_DISTANCE) {
                    g.setStroke(Color.web("#2dd4bf", 0.11 * (1 - d / LINK_DISTANCE)));
                    g.strokeLine(x1, y1, px[j] * w, py[j] * h);
                }
            }
        }
        g.setFill(Color.web("#5eead4", 0.45));
        for (int i = 0; i < PARTICLES; i++) {
            g.fillOval(px[i] * w - 1.4, py[i] * h - 1.4, 2.8, 2.8);
        }
    }

    private void drawHexGrid(GraphicsContext g, double w, double h) {
        double r = 30;
        double hexW = Math.sqrt(3) * r;
        double rowH = r * 1.5;
        g.setLineWidth(1);
        int row = 0;
        for (double y = -r; y < h + r; y += rowH, row++) {
            double shift = (row % 2 == 0) ? 0 : hexW / 2;
            for (double x = -hexW + shift; x < w + hexW; x += hexW) {
                double pulse = 0.5 + 0.5 * Math.sin(time * 0.7 + x * 0.013 + y * 0.017);
                double alpha = 0.022 + 0.05 * Math.pow(pulse, 6);
                g.setStroke(Color.web("#2dd4bf", alpha));
                double[] xs = new double[6];
                double[] ys = new double[6];
                for (int k = 0; k < 6; k++) {
                    double a = Math.toRadians(60 * k - 30);
                    xs[k] = x + r * Math.cos(a);
                    ys[k] = y + r * Math.sin(a);
                }
                g.strokePolygon(xs, ys, 6);
            }
        }
    }

    private void drawDataRain(GraphicsContext g, double w, double h, double dt) {
        g.setFont(Font.font("Consolas", 12));
        double colW = 38;
        int cols = (int) (w / colW) + 1;
        for (int c = 0; c < cols; c += 2) {
            double speed = 18 + (c * 37 % 23);
            double head = ((time * speed) + c * 97) % (h + 200) - 100;
            for (int k = 0; k < 9; k++) {
                double y = head - k * 15;
                if (y < 0 || y > h) {
                    continue;
                }
                int idx = (int) (time * 2 + c * 7 + k * 3) % HEX.length;
                double a = (k == 0 ? 0.22 : 0.10) * (1 - k / 9.0);
                g.setFill(Color.web("#5eead4", a));
                g.fillText(String.valueOf(HEX[idx]), c * colW + 10, y);
            }
        }
    }

    private void drawRadar(GraphicsContext g, double w, double h) {
        double cx = w * 0.82;
        double cy = h * 0.82;
        double radius = Math.min(w, h) * 0.34;
        g.setLineWidth(1);
        for (int i = 1; i <= 3; i++) {
            g.setStroke(Color.web("#2dd4bf", 0.05));
            g.strokeOval(cx - radius * i / 3, cy - radius * i / 3, radius * 2 * i / 3, radius * 2 * i / 3);
        }
        g.setStroke(Color.web("#2dd4bf", 0.05));
        g.strokeLine(cx - radius, cy, cx + radius, cy);
        g.strokeLine(cx, cy - radius, cx, cy + radius);
        double angle = (time * 40) % 360;
        g.setFill(new javafx.scene.paint.Color(0.176, 0.831, 0.749, 0.10));
        for (int i = 0; i < 24; i++) {
            g.setFill(Color.web("#2dd4bf", 0.07 * (1 - i / 24.0)));
            g.fillArc(cx - radius, cy - radius, radius * 2, radius * 2, -(angle - i * 1.5), -1.5, javafx.scene.shape.ArcType.ROUND);
        }
    }

    private static void glow(GraphicsContext g, double cx, double cy, double radius, Color color) {
        g.setFill(new RadialGradient(0, 0, cx, cy, radius, false, CycleMethod.NO_CYCLE,
                new Stop(0, color), new Stop(1, Color.TRANSPARENT)));
        g.fillRect(cx - radius, cy - radius, radius * 2, radius * 2);
    }
}

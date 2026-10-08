package com.sentinelav.desktop;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Animated status gauge: a breathing shield at rest and a radar sweep while scanning. */
final class ShieldGauge extends Canvas {
    enum State {
        READY("#2dd4bf"),
        SCANNING("#60a5fa"),
        ALERT("#fb5a6f"),
        ERROR("#f5b94a");

        private final Color color;

        State(String hex) {
            this.color = Color.web(hex);
        }
    }

    private State state = State.READY;
    private double red = State.READY.color.getRed();
    private double green = State.READY.color.getGreen();
    private double blue = State.READY.color.getBlue();
    private double sweepStrength;
    private double angle;
    private double phase;
    private long lastNanos;

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            double dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1_000_000_000.0, 0.1);
            lastNanos = now;
            advance(dt);
            draw();
        }
    };

    ShieldGauge(double size) {
        super(size, size);
        sceneProperty().addListener((observable, previous, scene) -> {
            if (scene == null) {
                timer.stop();
                lastNanos = 0;
            } else {
                timer.start();
            }
        });
        draw();
    }

    void setState(State next) {
        state = next;
    }

    private void advance(double dt) {
        phase += dt;
        double blend = 1 - Math.exp(-6 * dt);
        red += (state.color.getRed() - red) * blend;
        green += (state.color.getGreen() - green) * blend;
        blue += (state.color.getBlue() - blue) * blend;
        double target = state == State.SCANNING ? 1 : 0;
        sweepStrength += (target - sweepStrength) * (1 - Math.exp(-4 * dt));
        angle = (angle + dt * 170 * sweepStrength) % 360;
    }

    private static Color alpha(Color color, double value) {
        return Color.color(
                color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(1, value)));
    }

    private void draw() {
        double size = getWidth();
        double cx = size / 2;
        double cy = size / 2;
        double radius = size * 0.46;
        double inner = radius * 0.80;
        double breathe = 0.5 + 0.5 * Math.sin(phase * 1.6);
        Color color = Color.color(red, green, blue);
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, size, size);

        double glow = radius * (1.0 + 0.04 * breathe);
        g.setFill(new RadialGradient(0, 0, cx, cy, glow, false, CycleMethod.NO_CYCLE,
                new Stop(0.55, alpha(color, 0)),
                new Stop(0.82, alpha(color, 0.09 + 0.06 * breathe)),
                new Stop(1, alpha(color, 0))));
        g.fillOval(cx - glow, cy - glow, glow * 2, glow * 2);

        g.setFill(new RadialGradient(0, 0, cx, cy, inner, false, CycleMethod.NO_CYCLE,
                new Stop(0, alpha(color, 0.10)),
                new Stop(1, alpha(color, 0.02))));
        g.fillOval(cx - inner, cy - inner, inner * 2, inner * 2);

        double head = ((-angle) % 360 + 360) % 360;
        if (sweepStrength > 0.01) {
            for (int i = 0; i < 60; i++) {
                double fade = 1 - i / 60.0;
                g.setFill(alpha(color, 0.34 * fade * fade * sweepStrength));
                g.fillArc(cx - inner, cy - inner, inner * 2, inner * 2,
                        head + i * 2.0, 2.4, ArcType.ROUND);
            }
            double rad = Math.toRadians(head);
            g.setStroke(alpha(color, 0.9 * sweepStrength));
            g.setLineWidth(1.6);
            g.strokeLine(cx, cy, cx + Math.cos(rad) * inner, cy - Math.sin(rad) * inner);
        }

        g.setLineWidth(1.2);
        g.setStroke(alpha(color, 0.38));
        g.strokeOval(cx - inner, cy - inner, inner * 2, inner * 2);

        for (int i = 0; i < 90; i++) {
            double degrees = i * 4.0;
            double rad = Math.toRadians(degrees);
            boolean major = i % 5 == 0;
            double length = major ? 9 : 5;
            double opacity = major ? 0.55 : 0.26;
            if (sweepStrength > 0.01) {
                double behind = ((degrees - head) % 360 + 360) % 360;
                if (behind < 120) {
                    opacity += 0.6 * sweepStrength * (1 - behind / 120);
                }
            }
            g.setStroke(alpha(color, opacity));
            g.setLineWidth(major ? 1.6 : 1);
            g.strokeLine(
                    cx + Math.cos(rad) * radius, cy - Math.sin(rad) * radius,
                    cx + Math.cos(rad) * (radius - length), cy - Math.sin(rad) * (radius - length));
        }

        drawShield(g, color, cx, cy, radius * 0.36);
    }

    private void drawShield(GraphicsContext g, Color color, double cx, double cy, double s) {
        g.save();
        g.translate(cx, cy + s * 0.05);
        g.beginPath();
        g.moveTo(0, -s);
        g.bezierCurveTo(s * 0.35, -s * 0.80, s * 0.70, -s * 0.78, s * 0.90, -s * 0.70);
        g.lineTo(s * 0.90, s * 0.05);
        g.bezierCurveTo(s * 0.90, s * 0.55, s * 0.45, s * 0.85, 0, s * 1.05);
        g.bezierCurveTo(-s * 0.45, s * 0.85, -s * 0.90, s * 0.55, -s * 0.90, s * 0.05);
        g.lineTo(-s * 0.90, -s * 0.70);
        g.bezierCurveTo(-s * 0.70, -s * 0.78, -s * 0.35, -s * 0.80, 0, -s);
        g.closePath();
        g.setFill(alpha(color, 0.16));
        g.fill();
        g.setStroke(color);
        g.setLineWidth(2.4);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.setLineCap(StrokeLineCap.ROUND);
        g.stroke();

        g.setLineWidth(3);
        switch (state) {
            case READY -> BrandLogo.strokeS(g, s);
            case SCANNING -> {
                double pulse = 1 + 0.25 * Math.sin(phase * 5);
                g.strokeOval(-s * 0.22 * pulse, -s * 0.12 - s * 0.22 * pulse,
                        s * 0.44 * pulse, s * 0.44 * pulse);
            }
            case ALERT -> {
                g.strokeLine(0, -s * 0.42, 0, s * 0.10);
                g.strokeLine(0, s * 0.36, 0, s * 0.38);
            }
            case ERROR -> {
                g.strokeLine(-s * 0.26, -s * 0.22, s * 0.26, s * 0.30);
                g.strokeLine(s * 0.26, -s * 0.22, -s * 0.26, s * 0.30);
            }
        }
        g.restore();
    }
}

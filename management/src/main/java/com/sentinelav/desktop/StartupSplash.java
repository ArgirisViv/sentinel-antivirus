package com.sentinelav.desktop;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

final class StartupSplash extends StackPane {
    private static final Color TEAL = Color.web("#2dd4bf");
    private static final Color BLUE = Color.web("#60a5fa");

    private final Canvas background = new Canvas();
    private final ProgressBar progress = new ProgressBar();
    private final AnimationTimer animation;
    private final PauseTransition hold = new PauseTransition(Duration.millis(1850));

    StartupSplash() {
        getStyleClass().add("splash-root");
        setMinSize(760, 520);
        background.widthProperty().bind(widthProperty());
        background.heightProperty().bind(heightProperty());
        getChildren().add(background);

        Label wordmark = new Label("SENTINEL AV");
        wordmark.getStyleClass().add("splash-wordmark");
        Label identity = new Label("LOCAL SECURITY CONSOLE");
        identity.getStyleClass().add("splash-eyebrow");
        VBox brandCopy = new VBox(5, wordmark, identity);
        brandCopy.setAlignment(Pos.CENTER_LEFT);

        HBox brand = new HBox(17, BrandLogo.glowing(62), brandCopy);
        brand.setAlignment(Pos.CENTER_LEFT);

        Label heading = new Label("Security,\nin clear view.");
        heading.getStyleClass().add("splash-headline");
        Label description = new Label("Preparing your on-demand scanning workspace");
        description.getStyleClass().add("splash-description");
        description.setWrapText(true);
        description.setMaxWidth(380);

        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progress.getStyleClass().add("splash-progress");
        progress.setPrefWidth(320);
        progress.setMaxWidth(320);

        Circle statusDot = new Circle(3.5, TEAL);
        statusDot.getStyleClass().add("splash-status-dot");
        Label status = new Label("INITIALIZING LOCAL ENGINE");
        status.getStyleClass().add("splash-status");
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        Label localTag = new Label("ON-DEVICE");
        localTag.getStyleClass().add("splash-local-tag");
        HBox statusRow = new HBox(9, statusDot, status, spacer, localTag);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(24, brand, heading, description, progress, statusRow);
        content.getStyleClass().add("splash-content");
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(420);
        StackPane.setAlignment(content, Pos.CENTER_LEFT);
        StackPane.setMargin(content, new Insets(0, 0, 0, 76));
        getChildren().add(content);

        animation = new AnimationTimer() {
            private long start;

            @Override
            public void handle(long now) {
                if (start == 0) {
                    start = now;
                }
                drawBackground(now - start);
            }
        };
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                animation.start();
            } else {
                animation.stop();
            }
        });
    }

    void play(Runnable onFinished) {
        hold.setOnFinished(event -> {
            FadeTransition fade = new FadeTransition(Duration.millis(260), this);
            fade.setFromValue(1);
            fade.setToValue(0);
            fade.setOnFinished(done -> {
                animation.stop();
                onFinished.run();
            });
            fade.play();
        });
        hold.playFromStart();
    }

    void stop() {
        hold.stop();
        animation.stop();
    }

    private void drawBackground(long elapsedNanos) {
        double width = background.getWidth();
        double height = background.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        GraphicsContext g = background.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        g.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#080d12")),
                new Stop(0.55, Color.web("#0b1219")),
                new Stop(1, Color.web("#091116"))));
        g.fillRect(0, 0, width, height);

        double time = elapsedNanos / 1_000_000_000.0;
        double centerX = width * 0.755;
        double centerY = height * 0.5;
        double radius = Math.min(245, Math.min(height * 0.345, width * 0.235));

        g.setFill(new RadialGradient(0, 0, centerX, centerY, radius * 1.55, false,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0d5b58", 0.22)),
                new Stop(0.55, Color.web("#0b3b42", 0.11)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillRect(0, 0, width, height);

        drawAmbientSignals(g, width, height, time);
        drawRadar(g, centerX, centerY, radius, time);
    }

    private void drawAmbientSignals(GraphicsContext g, double width, double height, double time) {
        for (int i = 0; i < 52; i++) {
            double x = (i * 173.3 + 31) % width;
            double y = (i * 97.7 + 19) % height;
            double pulse = 0.5 + 0.5 * Math.sin(time * 0.8 + i * 1.7);
            double size = i % 9 == 0 ? 2.4 : 1.3;
            Color hue = i % 5 == 0 ? BLUE : TEAL;
            g.setFill(new Color(hue.getRed(), hue.getGreen(), hue.getBlue(),
                    0.12 + pulse * 0.24));
            g.fillOval(x, y, size, size);
        }

        g.setStroke(Color.web("#2dd4bf", 0.055));
        g.setLineWidth(1);
        g.beginPath();
        g.moveTo(width * 0.49, height * 0.17);
        g.bezierCurveTo(width * 0.57, height * 0.11, width * 0.59, height * 0.24,
                width * 0.65, height * 0.2);
        g.stroke();
        g.beginPath();
        g.moveTo(width * 0.47, height * 0.82);
        g.bezierCurveTo(width * 0.56, height * 0.88, width * 0.6, height * 0.74,
                width * 0.68, height * 0.79);
        g.stroke();
    }

    private void drawRadar(GraphicsContext g, double cx, double cy, double radius, double time) {
        g.setLineWidth(1);
        for (int ring = 1; ring <= 4; ring++) {
            double r = radius * ring / 4.0;
            g.setStroke(Color.web("#54d8ce", ring == 4 ? 0.13 : 0.075));
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        }

        g.setLineDashes(2, 7);
        g.setStroke(Color.web("#8ce8de", 0.19));
        g.strokeOval(cx - radius * 1.08, cy - radius * 1.08,
                radius * 2.16, radius * 2.16);
        g.setLineDashes();

        for (int tick = 0; tick < 96; tick++) {
            double angle = Math.PI * 2 * tick / 96.0;
            double inner = radius * (tick % 8 == 0 ? 1.015 : 1.045);
            double outer = radius * (tick % 8 == 0 ? 1.105 : 1.075);
            g.setStroke(Color.web(tick % 8 == 0 ? "#8ce8de" : "#54d8ce",
                    tick % 8 == 0 ? 0.38 : 0.16));
            g.setLineWidth(tick % 8 == 0 ? 1.2 : 0.8);
            g.strokeLine(cx + Math.cos(angle) * inner, cy + Math.sin(angle) * inner,
                    cx + Math.cos(angle) * outer, cy + Math.sin(angle) * outer);
        }

        double sweepAngle = (time * 34) % 360;
        g.setLineWidth(radius * 0.18);
        g.setStroke(Color.web("#2dd4bf", 0.035));
        g.strokeArc(cx - radius * 0.92, cy - radius * 0.92,
                radius * 1.84, radius * 1.84, sweepAngle - 48, 42, ArcType.OPEN);

        for (int ray = 0; ray < 20; ray++) {
            double offset = -48 + ray * 4.8;
            double angle = Math.toRadians(sweepAngle + offset);
            double intensity = 1 - Math.abs(offset) / 55.0;
            g.setStroke(Color.web("#2dd4bf", 0.012 + intensity * 0.045));
            g.setLineWidth(0.8);
            g.strokeLine(cx + Math.cos(angle) * radius * 0.15,
                    cy + Math.sin(angle) * radius * 0.15,
                    cx + Math.cos(angle) * radius * 0.96,
                    cy + Math.sin(angle) * radius * 0.96);
        }

        drawSignalNodes(g, cx, cy, radius, time);
        drawShield(g, cx, cy, radius, time);
    }

    private void drawSignalNodes(GraphicsContext g, double cx, double cy,
            double radius, double time) {
        double[] orbitRadii = {0.57, 0.79, 0.96, 0.68, 0.88};
        double[] startAngles = {28, 112, 198, 254, 326};
        double[] speeds = {13, -9, 7, -12, 10};
        double[] xs = new double[orbitRadii.length];
        double[] ys = new double[orbitRadii.length];

        for (int i = 0; i < orbitRadii.length; i++) {
            double angle = Math.toRadians(startAngles[i] + time * speeds[i]);
            xs[i] = cx + Math.cos(angle) * radius * orbitRadii[i];
            ys[i] = cy + Math.sin(angle) * radius * orbitRadii[i];
        }

        g.setLineWidth(1);
        int[][] links = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 0}, {0, 3}};
        for (int[] link : links) {
            g.setStroke(Color.web(link[0] % 2 == 0 ? "#2dd4bf" : "#60a5fa", 0.19));
            g.strokeLine(xs[link[0]], ys[link[0]], xs[link[1]], ys[link[1]]);
        }

        for (int i = 0; i < xs.length; i++) {
            double pulse = 1 + 0.65 * (0.5 + 0.5 * Math.sin(time * 2.4 + i));
            Color hue = i % 3 == 0 ? BLUE : TEAL;
            g.setFill(new Color(hue.getRed(), hue.getGreen(), hue.getBlue(), 0.12));
            g.fillOval(xs[i] - 7 * pulse, ys[i] - 7 * pulse, 14 * pulse, 14 * pulse);
            g.setFill(Color.web(i % 3 == 0 ? "#93c5fd" : "#8ce8de", 0.88));
            g.fillOval(xs[i] - 2.1, ys[i] - 2.1, 4.2, 4.2);
        }
    }

    private void drawShield(GraphicsContext g, double cx, double cy, double radius, double time) {
        double pulse = 0.5 + 0.5 * Math.sin(time * 1.2);
        double halo = radius * 0.31 + pulse * 5;
        g.setFill(Color.web("#2dd4bf", 0.035 + pulse * 0.025));
        g.fillOval(cx - halo, cy - halo, halo * 2, halo * 2);

        g.save();
        g.translate(cx, cy);
        double shieldSize = radius * 0.23;
        BrandLogo.shieldPath(g, shieldSize);
        g.setFill(new LinearGradient(0, -shieldSize, 0, shieldSize, false,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2dd4bf", 0.2)),
                new Stop(1, Color.web("#0d9488", 0.08))));
        g.fill();
        BrandLogo.shieldPath(g, shieldSize);
        g.setStroke(Color.web("#69e3d6", 0.8));
        g.setLineWidth(1.6);
        g.stroke();
        BrandLogo.strokeS(g, shieldSize * 0.58);
        g.setStroke(Color.web("#d6fff8", 0.9));
        g.setLineWidth(2.8);
        g.stroke();
        g.restore();

        g.setStroke(Color.web("#8ce8de", 0.15));
        g.setLineWidth(1);
        g.strokeOval(cx - radius * 0.29, cy - radius * 0.29,
                radius * 0.58, radius * 0.58);
    }
}

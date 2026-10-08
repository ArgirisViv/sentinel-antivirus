package com.sentinelav.desktop;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

final class StartupSplash extends StackPane {
    private static final Color TEAL = Color.web("#2dd4bf");

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

        Circle logoHalo = new Circle(65, Color.web("#2dd4bf", 0.035));
        logoHalo.setStroke(Color.web("#2dd4bf", 0.22));
        logoHalo.setStrokeWidth(1);
        StackPane logo = new StackPane(logoHalo, BrandLogo.glowing(96));
        logo.setMinSize(140, 140);
        logo.setMaxSize(140, 140);

        Label title = new Label("SENTINEL AV");
        title.getStyleClass().add("splash-title");
        Label eyebrow = new Label("LOCAL SECURITY CONSOLE");
        eyebrow.getStyleClass().add("splash-eyebrow");
        Label description = new Label("Preparing your on-demand scanning workspace");
        description.getStyleClass().add("splash-description");
        Label status = new Label("INITIALIZING SECURITY SYSTEMS");
        status.getStyleClass().add("splash-status");

        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progress.getStyleClass().add("splash-progress");
        progress.setPrefWidth(250);
        progress.setMaxWidth(250);

        VBox content = new VBox(12, logo, eyebrow, title, description, progress, status);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(520);
        StackPane.setAlignment(content, Pos.CENTER);
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
        g.setFill(Color.web("#0a0e13"));
        g.fillRect(0, 0, width, height);

        double centerX = width / 2;
        double centerY = height / 2;
        double time = elapsedNanos / 1_000_000_000.0;
        double pulse = (time * 42) % 260;
        g.setStroke(Color.web("#2dd4bf", 0.035));
        g.setLineWidth(1);
        for (double radius = pulse; radius < 560; radius += 260) {
            g.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        }

        g.setStroke(Color.web("#2dd4bf", 0.07));
        for (double x = centerX % 48; x < width; x += 48) {
            g.strokeLine(x, 0, x, height);
        }
        for (double y = centerY % 48; y < height; y += 48) {
            g.strokeLine(0, y, width, y);
        }

        double sweepX = (time * 115) % (width + 240) - 120;
        g.setFill(Color.web("#2dd4bf", 0.035));
        g.fillRect(sweepX - 60, 0, 120, height);
        g.setStroke(Color.web("#2dd4bf", 0.16));
        g.strokeLine(sweepX, 0, sweepX, height);

        for (int i = 0; i < 34; i++) {
            double x = (i * 173.0 + 37) % width;
            double y = (i * 97.0 + time * (14 + i % 9)) % height;
            double alpha = 0.10 + 0.08 * Math.sin(time * 1.5 + i);
            g.setFill(Color.web(i % 4 == 0 ? "#60a5fa" : "#2dd4bf", alpha));
            g.fillOval(x, y, i % 5 == 0 ? 3 : 2, i % 5 == 0 ? 3 : 2);
        }
    }
}

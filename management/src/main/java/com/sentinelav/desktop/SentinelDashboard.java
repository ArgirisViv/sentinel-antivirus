package com.sentinelav.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SentinelDashboard extends Application {
    private static final String BACKGROUND = "#0b1220";
    private static final String PANEL = "#111c2e";
    private static final String MUTED = "#94a3b8";
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern SCAN_SUMMARY = Pattern.compile(
            "Scan complete: (\\d+) file\\(s\\) scanned, (\\d+) detection\\(s\\), "
                    + "(\\d+) suspicious file\\(s\\)");

    private final EngineClient engine = new EngineClient();
    private final ObservableList<String> history = FXCollections.observableArrayList();
    private final TextField enginePath = new TextField(defaultEnginePath());
    private final TextField configPath = new TextField(defaultConfigPath());
    private final TextField targetPath = new TextField();
    private final TextField signaturesPath = new TextField(defaultSignaturesPath());
    private final TextField quarantinePath = new TextField();
    private final CheckBox quarantineEnabled = new CheckBox("Quarantine detections");
    private final TextArea activity = new TextArea();
    private final Label connectionValue = new Label("Not connected");
    private final Label modeValue = new Label("Idle");
    private final Label filesValue = new Label("—");
    private final Label detectionsValue = new Label("0");
    private final Label suspiciousValue = new Label("0");
    private final Button scanButton = new Button("Scan now");
    private final Button watchButton = new Button("Watch folder");
    private final Button processButton = new Button("Monitor processes");
    private final Button stopButton = new Button("Stop");

    @Override
    public void start(Stage stage) {
        stage.setTitle("Sentinel AV | Management");
        stage.setMinWidth(920);
        stage.setMinHeight(680);
        stage.setOnCloseRequest(event -> engine.close());

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BACKGROUND + ";");
        root.setTop(buildHeader());
        root.setCenter(buildWorkspace(stage));
        root.setBottom(buildFooter());

        Scene scene = new Scene(root, 1120, 780);
        stage.setScene(scene);
        stage.show();
        addHistory("INFO", "Management dashboard started.");
    }

    private VBox buildHeader() {
        Label brand = new Label("SENTINEL");
        brand.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #38bdf8;");
        Label title = new Label("Endpoint Security");
        title.setStyle("-fx-font-size: 25px; -fx-font-weight: bold; -fx-text-fill: #f1f5f9;");
        Label subtitle = new Label("Local engine control and security activity");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + MUTED + ";");

        VBox titles = new VBox(5, brand, title, subtitle);
        HBox header = new HBox(titles);
        header.setPadding(new Insets(24, 30, 20, 30));
        header.setStyle("-fx-border-color: transparent transparent #1e293b transparent;");
        return new VBox(header);
    }

    private VBox buildWorkspace(Stage stage) {
        VBox content = new VBox(18);
        content.setPadding(new Insets(22, 30, 20, 30));

        HBox metrics = new HBox(12,
                metricCard("ENGINE", connectionValue),
                metricCard("MODE", modeValue),
                metricCard("FILES SCANNED", filesValue),
                metricCard("DETECTIONS", detectionsValue),
                metricCard("SUSPICIOUS", suspiciousValue));
        for (javafx.scene.Node node : metrics.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        VBox settings = panel("Engine and scan settings");
        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(11);
        fields.add(fieldLabel("Engine executable"), 0, 0);
        fields.add(enginePath, 1, 0);
        Button browseEngine = new Button("Browse");
        browseEngine.setOnAction(event -> chooseEngine(stage));
        fields.add(browseEngine, 2, 0);

        fields.add(fieldLabel("Engine config"), 0, 1);
        fields.add(configPath, 1, 1);
        Button browseConfig = new Button("Browse");
        browseConfig.setOnAction(event -> chooseConfig(stage));
        fields.add(browseConfig, 2, 1);

        fields.add(fieldLabel("Scan target"), 0, 2);
        fields.add(targetPath, 1, 2);
        HBox targetButtons = new HBox(6,
                browseButton("Folder", () -> chooseDirectory(stage, targetPath)),
                browseButton("File", () -> chooseTargetFile(stage)));
        fields.add(targetButtons, 2, 2);

        fields.add(fieldLabel("Signature database"), 0, 3);
        fields.add(signaturesPath, 1, 3);
        Button browseSignatures = new Button("Browse");
        browseSignatures.setOnAction(event -> chooseSignatures(stage));
        fields.add(browseSignatures, 2, 3);

        fields.add(fieldLabel("Quarantine"), 0, 4);
        fields.add(quarantinePath, 1, 4);
        Button browseQuarantine = new Button("Browse");
        browseQuarantine.setOnAction(event -> chooseDirectory(stage, quarantinePath));
        fields.add(browseQuarantine, 2, 4);
        fields.add(quarantineEnabled, 1, 5);
        GridPane.setHgrow(enginePath, Priority.ALWAYS);
        GridPane.setHgrow(configPath, Priority.ALWAYS);
        GridPane.setHgrow(targetPath, Priority.ALWAYS);
        GridPane.setHgrow(signaturesPath, Priority.ALWAYS);
        GridPane.setHgrow(quarantinePath, Priority.ALWAYS);
        fields.setStyle("-fx-alignment: center-left;");
        settings.getChildren().add(fields);

        scanButton.setOnAction(event -> runPathCommand(stage, "scan"));
        watchButton.setOnAction(event -> runPathCommand(stage, "watch"));
        processButton.setOnAction(event -> runProcessMonitor());
        stopButton.setOnAction(event -> stopEngine());
        stopButton.setDisable(true);
        scanButton.getStyleClass().add("primary-button");
        watchButton.getStyleClass().add("secondary-button");
        processButton.getStyleClass().add("secondary-button");
        stopButton.getStyleClass().add("danger-button");
        scanButton.setStyle(
                "-fx-background-color: #0284c7; -fx-text-fill: white;"
                        + "-fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 9 15;");
        watchButton.setStyle(
                "-fx-background-color: #1e293b; -fx-text-fill: #e2e8f0;"
                        + "-fx-background-radius: 6; -fx-padding: 9 15;");
        processButton.setStyle(
                "-fx-background-color: #1e293b; -fx-text-fill: #e2e8f0;"
                        + "-fx-background-radius: 6; -fx-padding: 9 15;");
        stopButton.setStyle(
                "-fx-background-color: #7f1d1d; -fx-text-fill: #fecaca;"
                        + "-fx-background-radius: 6; -fx-padding: 9 15;");
        styleTextField(enginePath);
        styleTextField(configPath);
        styleTextField(targetPath);
        styleTextField(signaturesPath);
        styleTextField(quarantinePath);
        quarantineEnabled.setStyle("-fx-text-fill: #cbd5e1;");

        HBox controls = new HBox(9, scanButton, watchButton, processButton, stopButton);
        controls.setAlignment(Pos.CENTER_LEFT);
        settings.getChildren().add(controls);

        VBox activityPanel = panel("Live engine output");
        activity.setEditable(false);
        activity.setWrapText(false);
        activity.setPromptText("Engine output will appear here when an operation starts.");
        activity.setPrefRowCount(8);
        activity.setStyle(
                "-fx-control-inner-background: #080e19; -fx-text-fill: #cbd5e1;"
                        + "-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
        VBox.setVgrow(activity, Priority.ALWAYS);
        activityPanel.getChildren().add(activity);

        VBox historyPanel = panel("Recent events");
        ListView<String> historyList = new ListView<>(history);
        historyList.setPrefHeight(112);
        historyList.setStyle(
                "-fx-control-inner-background: " + PANEL + "; -fx-text-fill: #cbd5e1;");
        historyList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle("-fx-background-color: " + PANEL + "; -fx-text-fill: #cbd5e1;");
            }
        });
        historyPanel.getChildren().add(historyList);

        content.getChildren().addAll(metrics, settings, activityPanel, historyPanel);
        VBox.setVgrow(activityPanel, Priority.ALWAYS);
        return content;
    }

    private HBox metricCard(String title, Label value) {
        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + MUTED + ";");
        value.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");
        VBox card = new VBox(9, heading, value);
        card.setPadding(new Insets(14));
        card.setStyle(
                "-fx-background-color: " + PANEL + "; -fx-background-radius: 10;"
                        + "-fx-border-color: #1e293b; -fx-border-radius: 10;");
        HBox wrapper = new HBox(card);
        HBox.setHgrow(card, Priority.ALWAYS);
        return wrapper;
    }

    private VBox panel(String title) {
        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");
        VBox panel = new VBox(13, heading);
        panel.setPadding(new Insets(16));
        panel.setStyle(
                "-fx-background-color: " + PANEL + "; -fx-background-radius: 10;"
                        + "-fx-border-color: #1e293b; -fx-border-radius: 10;");
        return panel;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");
        return label;
    }

    private void styleTextField(TextField field) {
        field.setStyle(
                "-fx-control-inner-background: #080e19; -fx-text-fill: #e2e8f0;"
                        + "-fx-prompt-text-fill: #64748b; -fx-background-radius: 5;"
                        + "-fx-border-color: #334155; -fx-border-radius: 5;");
    }

    private Button browseButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        return button;
    }

    private HBox buildFooter() {
        Label note = new Label(
                "Sentinel AV is a learning project. Detection is signature-based and best-effort.");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: " + MUTED + ";");
        HBox footer = new HBox(note);
        footer.setPadding(new Insets(10, 30, 16, 30));
        return footer;
    }

    private void chooseEngine(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Sentinel AV engine");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Windows executable", "*.exe"));
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            enginePath.setText(selected.getAbsolutePath());
            connectionValue.setText("Configured");
        }
    }

    private void chooseTargetFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a file to scan");
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            targetPath.setText(selected.getAbsolutePath());
        }
    }

    private void chooseConfig(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Sentinel AV configuration");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Configuration files", "*.ini", "*.conf"));
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            configPath.setText(selected.getAbsolutePath());
        }
    }

    private void chooseSignatures(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select signature database");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text files", "*.txt"));
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            signaturesPath.setText(selected.getAbsolutePath());
        }
    }

    private void chooseDirectory(Stage stage, TextField target) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select directory");
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            target.setText(selected.getAbsolutePath());
        }
    }

    private void runPathCommand(Stage stage, String command) {
        Path target = existingPath(targetPath.getText(), "Select a file or directory to scan.");
        if (target == null) {
            return;
        }
        List<String> arguments = baseCommand(command, target.toString());
        if (arguments == null) {
            return;
        }

        if (quarantineEnabled.isSelected()) {
            String quarantine = quarantinePath.getText().trim();
            if (quarantine.isEmpty() && configPath.getText().isBlank()) {
                addHistory("ERROR", "Choose a quarantine directory or disable quarantine.");
                return;
            }
            arguments.add("--quarantine");
            if (!quarantine.isEmpty()) {
                try {
                    arguments.add(Path.of(quarantine).toAbsolutePath().normalize().toString());
                } catch (RuntimeException error) {
                    addHistory("ERROR", "Invalid quarantine path: " + error.getMessage());
                    return;
                }
            }
        }

        startEngine(arguments, command.equals("scan") ? "Scanning" : "Watching");
    }

    private void runProcessMonitor() {
        List<String> arguments = baseCommand("processes", null);
        if (arguments != null) {
            if (quarantineEnabled.isSelected()) {
                addHistory("WARN", "Quarantine is not available for process monitoring.");
            }
            startEngine(arguments, "Monitoring processes");
        }
    }

    private List<String> baseCommand(String command, String target) {
        Path executable = existingPath(enginePath.getText(), "Select the Sentinel AV executable.");
        if (executable == null) {
            return null;
        }
        Path signatures = existingPath(
                signaturesPath.getText(), "Select the SHA-256 signature database.");
        if (signatures == null) {
            return null;
        }

        List<String> arguments = new ArrayList<>();
        arguments.add(executable.toString());
        arguments.add(command);
        if (target != null) {
            arguments.add(target);
        }
        if (!configPath.getText().isBlank()) {
            Path config = existingPath(
                    configPath.getText(), "Select a valid engine configuration file.");
            if (config == null) {
                return null;
            }
            arguments.add("--config");
            arguments.add(config.toString());
        }
        arguments.add("--signatures");
        arguments.add(signatures.toString());
        return arguments;
    }

    private Path existingPath(String value, String missingMessage) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            addHistory("ERROR", missingMessage);
            return null;
        }
        try {
            Path path = Path.of(trimmed).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                addHistory("ERROR", "Path does not exist: " + path);
                return null;
            }
            return path;
        } catch (RuntimeException error) {
            addHistory("ERROR", "Invalid path: " + error.getMessage());
            return null;
        }
    }

    private void startEngine(List<String> arguments, String mode) {
        try {
            engine.start(
                    arguments,
                    this::receiveOutput,
                    (exitCode, failure) -> Platform.runLater(() -> {
                        setBusy(false);
                        connectionValue.setText("Ready");
                        if (failure != null) {
                            modeValue.setText("Error");
                            addHistory("ERROR", "Engine communication failed: " + failure.getMessage());
                        } else if (exitCode == 0) {
                            modeValue.setText("Idle");
                            addHistory("INFO", "Engine operation completed.");
                        } else {
                            modeValue.setText("Error");
                            addHistory("ERROR", "Engine exited with code " + exitCode + ".");
                        }
                    }));
            setBusy(true);
            modeValue.setText(mode);
            connectionValue.setText("Connected");
            activity.appendText("$ " + String.join(" ", arguments) + System.lineSeparator());
            addHistory("INFO", mode + " started.");
        } catch (Exception error) {
            modeValue.setText("Error");
            connectionValue.setText("Unavailable");
            addHistory("ERROR", "Could not start engine: " + error.getMessage());
        }
    }

    private void receiveOutput(String line) {
        Platform.runLater(() -> {
            appendActivity(line);
            if (line.contains("[DETECTED]")) {
                detectionsValue.setText(
                        Integer.toString(Integer.parseInt(detectionsValue.getText()) + 1));
                addHistory("DETECTION", line);
            } else if (line.contains("[SUSPICIOUS]") ||
                       line.contains("[SUSPICIOUS PROCESS]")) {
                suspiciousValue.setText(
                        Integer.toString(Integer.parseInt(suspiciousValue.getText()) + 1));
                addHistory("SUSPICIOUS", line);
            } else if (line.contains("[PROCESS START]")) {
                addHistory("PROCESS", line);
            } else if (line.startsWith("[ERROR]") || line.startsWith("[FATAL]")) {
                addHistory("ERROR", line);
            } else {
                Matcher matcher = SCAN_SUMMARY.matcher(line);
                if (matcher.find()) {
                    filesValue.setText(matcher.group(1));
                    detectionsValue.setText(matcher.group(2));
                    suspiciousValue.setText(matcher.group(3));
                    addHistory("SCAN", line);
                }
            }
        });
    }

    private void appendActivity(String line) {
        activity.appendText(line + System.lineSeparator());
        if (activity.getParagraphs().size() > 1000) {
            int nextLine = activity.getText().indexOf('\n');
            if (nextLine >= 0) {
                activity.deleteText(0, nextLine + 1);
            }
        }
    }

    private void stopEngine() {
        if (engine.isRunning()) {
            engine.stop();
            modeValue.setText("Stopping");
            addHistory("INFO", "Stop requested for active engine process.");
        }
    }

    private void setBusy(boolean busy) {
        scanButton.setDisable(busy);
        watchButton.setDisable(busy);
        processButton.setDisable(busy);
        stopButton.setDisable(!busy);
    }

    private void addHistory(String kind, String message) {
        history.add(0, LocalDateTime.now().format(TIME) + "  " + kind + "  " + message);
        if (history.size() > 100) {
            history.remove(history.size() - 1);
        }
    }

    private static String defaultEnginePath() {
        for (Path candidate : List.of(
                Path.of("build", "Release", "sentinel-av.exe"),
                Path.of("build", "sentinel-av.exe"),
                Path.of("..", "build", "Release", "sentinel-av.exe"),
                Path.of("..", "build", "sentinel-av.exe"))) {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.exists(absolute)) {
                return absolute.toString();
            }
        }
        return Path.of("..", "build", "sentinel-av.exe").toAbsolutePath().normalize().toString();
    }

    private static String defaultSignaturesPath() {
        for (Path candidate : List.of(
                Path.of("signatures.example.txt"),
                Path.of("..", "signatures.example.txt"))) {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.exists(absolute)) {
                return absolute.toString();
            }
        }
        return Path.of("..", "signatures.example.txt")
                .toAbsolutePath().normalize().toString();
    }

    private static String defaultConfigPath() {
        for (Path candidate : List.of(
                Path.of("config.example.ini"),
                Path.of("..", "config.example.ini"))) {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.exists(absolute)) {
                return absolute.toString();
            }
        }
        return "";
    }

    @Override
    public void stop() {
        engine.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

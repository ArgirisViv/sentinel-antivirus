package com.sentinelav.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SentinelDashboard extends Application {
    private static final String NAVY = "#171426";
    private static final String NAVY_LIGHT = "#272343";
    private static final String BACKGROUND = "#100d1c";
    private static final String CARD = "#211d37";
    private static final String TEXT = "#f4f0ff";
    private static final String MUTED = "#a6a1bd";
    private static final String CYAN = "#87b9ff";
    private static final String GREEN = "#53d9a2";
    private static final String BORDER = "#38324e";
    private static final String PINK = "#f32669";
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern SCAN_SUMMARY = Pattern.compile(
            "Scan complete: (\\d+) file\\(s\\) scanned, (\\d+) detection\\(s\\), "
                    + "(\\d+) suspicious file\\(s\\)");
    private static final Pattern QUICK_SCAN_SUMMARY = Pattern.compile(
            "Quick scan complete: (\\d+) location\\(s\\) scanned, "
                    + "(\\d+) file\\(s\\) scanned, (\\d+) detection\\(s\\), "
                    + "(\\d+) suspicious file\\(s\\), (\\d+) skipped, "
                    + "(\\d+) error\\(s\\), highest file risk (\\d+)/100 \\(([^)]+)\\)");
    private static final Pattern FILE_RISK = Pattern.compile(
            "\\[RISK\\].*\\| score=(\\d+) \\| severity=([^|]+) \\| "
                    + "category=([^|]+) \\| confidence=([^|]+)");

    private final EngineClient engine = new EngineClient();
    private final ObservableList<String> history = FXCollections.observableArrayList();
    private final ObservableList<QuarantineItem> quarantinedItems =
            FXCollections.observableArrayList();
    private final TextField enginePath = new TextField(defaultEnginePath());
    private final TextField configPath = new TextField(defaultConfigPath());
    private final TextField signaturesPath = new TextField(defaultSignaturesPath());
    private final TextField quarantinePath = new TextField();
    private final CheckBox quarantineEnabled = new CheckBox(
            "Move exact signature matches to quarantine after a scan");
    private final TextArea activity = new TextArea();
    private final Label engineStatus = new Label("Idle");
    private final Label modeValue = new Label("Ready");
    private final Label filesValue = new Label("—");
    private final Label detectionsValue = new Label("0");
    private final Label suspiciousValue = new Label("0");
    private final Label riskValue = new Label("No indicators");
    private final Label selectedTarget = new Label("No folder selected");
    private final Label statusDot = new Label("●");
    private final Button stopButton = new Button("Stop");
    private final StackPane pageHost = new StackPane();
    private ListView<QuarantineItem> quarantineEntries;
    private final Label pageTitle = new Label();
    private final Label pageDescription = new Label();
    private final Label heroStatus = new Label("YOUR DEVICE IS READY");
    private final Label statusDescription = new Label(
            "On-demand scans are available. Background protection is not active.");
    private final List<Button> navigationButtons = new ArrayList<>();
    private final List<Button> operationButtons = new ArrayList<>();

    private Stage stage;
    private boolean stopRequestedByUser;
    private int highestRiskScoreObserved;

    private record QuarantineItem(
            String id,
            String sha256,
            String threat,
            String timestamp,
            String originalPath) {
        @Override
        public String toString() {
            return threat + "  |  " + originalPath + "  |  " + timestamp
                    + "  |  SHA-256 " + sha256;
        }
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("Sentinel AV");
        stage.setMinWidth(1080);
        stage.setMinHeight(740);
        stage.setOnCloseRequest(event -> engine.close());

        StackPane root = new StackPane();
        root.setPadding(new Insets(20));
        root.setStyle(
                "-fx-background-color: radial-gradient(center 82% 10%, radius 90%, "
                        + "#321126 0%, #1b1023 45%, #100d1c 100%);");
        Circle upperGlow = new Circle(230);
        upperGlow.setFill(Color.TRANSPARENT);
        upperGlow.setStroke(Color.web("#f32669", 0.12));
        upperGlow.setStrokeWidth(1.2);
        upperGlow.setMouseTransparent(true);
        StackPane.setAlignment(upperGlow, Pos.TOP_RIGHT);
        upperGlow.setTranslateX(125);
        upperGlow.setTranslateY(-165);
        Circle lowerGlow = new Circle(285);
        lowerGlow.setFill(Color.TRANSPARENT);
        lowerGlow.setStroke(Color.web("#a378ff", 0.1));
        lowerGlow.setStrokeWidth(1);
        lowerGlow.setMouseTransparent(true);
        StackPane.setAlignment(lowerGlow, Pos.BOTTOM_LEFT);
        lowerGlow.setTranslateX(-190);
        lowerGlow.setTranslateY(205);
        BorderPane shell = new BorderPane();
        shell.setLeft(buildSidebar());
        shell.setCenter(buildMainArea());
        shell.setBottom(buildBrandFooter());
        shell.setStyle(
                "-fx-background-color: " + NAVY + "; -fx-background-radius: 18;"
                        + "-fx-border-color: #44314d; -fx-border-radius: 18;"
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 28, 0.15, 0, 12);");
        root.getChildren().addAll(upperGlow, lowerGlow, shell);

        Scene scene = new Scene(root, 1380, 900);
        stage.setScene(scene);
        modeValue.textProperty().addListener(
                (observable, previous, current) -> updateHeroStatus(current));
        showPage("Overview");
        stage.show();
        addHistory("INFO", "Sentinel AV is ready for an on-demand scan.");
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(7);
        sidebar.setPrefWidth(184);
        sidebar.setMinWidth(168);
        sidebar.setPadding(new Insets(24, 12, 18, 12));
        sidebar.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #1a172d, " + NAVY + ");"
                        + "-fx-background-radius: 18 0 0 18;"
                        + "-fx-border-color: transparent #302a43 transparent transparent;");

        HBox brand = new HBox(8);
        brand.setAlignment(Pos.CENTER_LEFT);
        StackPane mark = buildLogoMark();
        mark.setScaleX(0.72);
        mark.setScaleY(0.72);
        VBox brandText = new VBox(2,
                styledLabel("SENTINEL", "#faf7ff", 12, true),
                styledLabel("SECURITY", "#aaa3c0", 8, true));
        brand.getChildren().addAll(mark, brandText);
        Label menuCaption = styledLabel("WORKSPACE", "#77718c", 8, true);
        menuCaption.setPadding(new Insets(26, 9, 7, 9));
        sidebar.getChildren().addAll(brand, menuCaption);

        List<String> pages = List.of(
                "Dashboard", "Protection", "Scanner", "Threats", "Quarantine", "Processes", "Settings");
        List<String> symbols = List.of("⌂", "◈", "⌕", "!", "⬡", "◉", "⚙");
        for (int index = 0; index < pages.size(); index++) {
            String page = pages.get(index);
            Button button = new Button(page);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinHeight(37);
            button.setPadding(new Insets(0, 10, 0, 10));
            Label symbol = styledLabel(symbols.get(index), "#b7afd0", 12, true);
            symbol.setMinWidth(16);
            button.setGraphic(symbol);
            button.setGraphicTextGap(10);
            button.setStyle(navStyle(false));
            button.setOnAction(event -> showPage(page));
            navigationButtons.add(button);
            sidebar.getChildren().add(button);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox localStatus = new VBox(7);
        localStatus.setPadding(new Insets(12, 10, 12, 10));
        localStatus.setStyle(
                "-fx-background-color: #211c35; -fx-background-radius: 9;"
                        + "-fx-border-color: #39324f; -fx-border-radius: 9;");
        HBox statusLine = new HBox(6, new Circle(3, Color.web(GREEN)),
                styledLabel("LOCAL ENGINE", "#eee9fa", 8, true));
        statusLine.setAlignment(Pos.CENTER_LEFT);
        Label localDescription = styledLabel(
                "Manual, on-demand scans. No always-on protection.",
                "#aaa3bd", 9, false);
        localDescription.setWrapText(true);
        localStatus.getChildren().addAll(statusLine, localDescription);
        sidebar.getChildren().addAll(spacer, localStatus);
        return sidebar;
    }

    private HBox buildBrandFooter() {
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(0, 18, 0, 18));
        footer.setMinHeight(36);
        footer.setStyle(
                "-fx-background-color: linear-gradient(to right, #e91f62, #bd245e);"
                        + "-fx-background-radius: 0 0 18 18;");
        Label name = styledLabel("SENTINEL AV", "#fff8fb", 10, true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox status = new HBox(6, new Circle(3, Color.web("#ffe6ef")),
                styledLabel("ON-DEMAND SCANNING  ·  NO ALWAYS-ON PROTECTION", "#ffe0e9", 8, true));
        status.setAlignment(Pos.CENTER_RIGHT);
        footer.getChildren().addAll(name, spacer, status);
        return footer;
    }

    private StackPane buildLogoMark() {
        StackPane mark = new StackPane();
        mark.setMinSize(44, 44);
        mark.setPrefSize(44, 44);
        mark.setMaxSize(44, 44);
        mark.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #d73d75, #833859);"
                        + "-fx-background-radius: 11; -fx-border-color: #ff9fc2;"
                        + "-fx-border-radius: 11;"
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.28), 10, 0, 0, 3);");

        SVGPath shield = new SVGPath();
        shield.setContent(
                "M24 3 L39 9 L38 21 C37 30 31 37 24 42 "
                        + "C17 37 11 30 10 21 L9 9 Z");
        shield.setFill(Color.web("#f4a6c3"));
        shield.setStroke(Color.web("#fff0f6"));
        shield.setStrokeWidth(1.1);

        SVGPath sentinelS = new SVGPath();
        sentinelS.setContent(
                "M31 14 C28 10 19 10 16 15 C13 20 20 22 25 24 "
                        + "C31 26 30 33 24 35 C20 37 15 35 13 32");
        sentinelS.setFill(Color.TRANSPARENT);
        sentinelS.setStroke(Color.web("#54263d"));
        sentinelS.setStrokeWidth(5.2);
        sentinelS.setStrokeLineCap(StrokeLineCap.ROUND);
        sentinelS.setStrokeLineJoin(StrokeLineJoin.ROUND);
        SVGPath innerS = new SVGPath();
        innerS.setContent(sentinelS.getContent());
        innerS.setFill(Color.TRANSPARENT);
        innerS.setStroke(Color.web("#fff4f8"));
        innerS.setStrokeWidth(2.2);
        innerS.setStrokeLineCap(StrokeLineCap.ROUND);
        innerS.setStrokeLineJoin(StrokeLineJoin.ROUND);
        mark.getChildren().addAll(shield, sentinelS, innerS);
        return mark;
    }

    private VBox buildMainArea() {
        VBox main = new VBox();
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.setStyle(
                "-fx-background-color: #19162b;"
                        + "-fx-border-color: transparent transparent " + BORDER + " transparent;");

        VBox heading = new VBox(3,
                styledLabel("SENTINEL  /  LOCAL SECURITY", "#9d96b5", 8, true),
                pageTitle);
        pageTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + TEXT + ";");
        pageDescription.setVisible(false);
        pageDescription.setManaged(false);
        HBox.setHgrow(heading, Priority.ALWAYS);

        Label engineChip = styledLabel("ENGINE V0.1.0", "#c6c0d9", 8, true);
        engineChip.setPadding(new Insets(7, 9, 7, 9));
        engineChip.setStyle(
                "-fx-background-color: #26213c; -fx-background-radius: 12;"
                        + "-fx-text-fill: #c6c0d9; -fx-font-size: 8px; -fx-font-weight: bold;");

        statusDot.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");
        engineStatus.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 9px; -fx-font-weight: bold;");
        HBox engineState = new HBox(6, statusDot, engineStatus);
        engineState.setAlignment(Pos.CENTER);
        engineState.setPadding(new Insets(7, 10, 7, 10));
        engineState.setStyle(
                "-fx-background-color: #28233d; -fx-background-radius: 16;"
                        + "-fx-border-color: #433958; -fx-border-radius: 16;");

        Button alerts = new Button("♢");
        alerts.setTooltip(new javafx.scene.control.Tooltip("Open threat and activity history"));
        alerts.setOnAction(event -> showPage("Threats"));
        alerts.setStyle(
                "-fx-background-color: #28233d; -fx-text-fill: #d7d0e8;"
                        + "-fx-font-size: 14px; -fx-background-radius: 16;"
                        + "-fx-min-width: 30px; -fx-min-height: 30px;");
        Button settings = new Button("⚙");
        settings.setTooltip(new javafx.scene.control.Tooltip("Open settings"));
        settings.setOnAction(event -> showPage("Settings"));
        settings.setStyle(
                "-fx-background-color: #28233d; -fx-text-fill: #d7d0e8;"
                        + "-fx-font-size: 12px; -fx-background-radius: 16;"
                        + "-fx-min-width: 30px; -fx-min-height: 30px;");
        StackPane avatar = new StackPane();
        Circle avatarCircle = new Circle(13, Color.web("#453261"));
        avatarCircle.setStroke(Color.web("#8565b6"));
        Label initials = styledLabel("S", "#f3eaff", 9, true);
        avatar.getChildren().addAll(avatarCircle, initials);
        avatar.setMinSize(28, 28);
        avatar.setPrefSize(28, 28);
        stopButton.setDisable(true);
        stopButton.setOnAction(event -> stopEngine());
        stopButton.setStyle(
                "-fx-background-color: #3b2037; -fx-text-fill: #ffabc9;"
                        + "-fx-font-weight: bold; -fx-font-size: 9px;"
                        + "-fx-background-radius: 6; -fx-padding: 7 12;");
        header.getChildren().addAll(
                heading,
                engineChip,
                alerts,
                settings,
                engineState,
                avatar,
                stopButton);

        ScrollPane scroll = new ScrollPane(pageHost);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle(
                "-fx-background: " + BACKGROUND + "; -fx-background-color: " + BACKGROUND + ";"
                        + "-fx-padding: 0; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        main.getChildren().addAll(header, scroll);
        return main;
    }

    private void showPage(String page) {
        operationButtons.clear();
        String selectedPage = switch (page) {
            case "Overview" -> "Dashboard";
            case "Scan" -> "Scanner";
            case "Monitoring" -> "Protection";
            case "Activity" -> "Threats";
            default -> page;
        };
        pageHost.getChildren().clear();
        pageTitle.setText(selectedPage);
        pageDescription.setText(switch (selectedPage) {
            case "Dashboard" -> "Your device status at a glance.";
            case "Protection" -> "Manual folder and process monitoring controls.";
            case "Scanner" -> "Scan Downloads or choose a file, folder, or drive.";
            case "Threats" -> "Detections and security events from this session.";
            case "Quarantine" -> "Configure optional isolation for signature matches.";
            case "Processes" -> "Inspect newly started processes on demand.";
            case "Settings" -> "Configure the local engine and signature database.";
            default -> "";
        });
        for (Button button : navigationButtons) {
            button.setStyle(navStyle(button.getText().equals(selectedPage)));
        }
        Node content = switch (selectedPage) {
            case "Scanner" -> buildScanPage();
            case "Protection" -> buildMonitoringPage(false);
            case "Processes" -> buildMonitoringPage(true);
            case "Threats" -> buildActivityPage();
            case "Quarantine" -> buildQuarantinePage();
            case "Settings" -> buildSettingsPage();
            default -> buildOverviewPage();
        };
        pageHost.getChildren().setAll(content);
        operationButtons.forEach(button -> button.setDisable(engine.isRunning()));
    }

    private VBox buildOverviewPage() {
        VBox content = pageContent();
        content.setSpacing(14);
        content.setPadding(new Insets(18, 22, 20, 22));
        content.setMaxHeight(Double.MAX_VALUE);

        HBox hero = buildSecurityBanner();
        GridPane actions = buildActionGrid();
        VBox.setVgrow(actions, Priority.ALWAYS);
        HBox facts = buildSecurityFacts();
        content.getChildren().addAll(hero, actions, facts);
        return content;
    }

    private HBox buildSecurityBanner() {
        HBox banner = new HBox(14);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(17, 23, 17, 23));
        banner.setMinHeight(116);
        banner.setStyle(
                "-fx-background-color: linear-gradient(to right, #f02567 0%, #bf205d 58%, #702752 100%);"
                        + "-fx-background-radius: 14;"
                        + "-fx-effect: dropshadow(gaussian, rgba(226,29,91,0.22), 18, 0.12, 0, 5);");

        VBox message = new VBox(6);
        Label eyebrow = styledLabel("SENTINEL SECURITY CENTER", "#ffe2ed", 9, true);
        heroStatus.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        statusDescription.setStyle("-fx-text-fill: #ffe0e9; -fx-font-size: 10px;");
        Label subheading = statusDescription;
        subheading.setWrapText(true);
        message.getChildren().addAll(eyebrow, heroStatus, subheading);
        HBox.setHgrow(message, Priority.ALWAYS);

        Button scan = new Button("SCAN DOWNLOADS  →");
        scan.setMinHeight(34);
        scan.setStyle(
                "-fx-background-color: #35172f; -fx-text-fill: white;"
                        + "-fx-font-size: 11px; -fx-font-weight: bold;"
                        + "-fx-background-radius: 8; -fx-padding: 0 13 0 13;"
                        + "-fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 8;");
        registerOperationButton(scan);
        scan.setOnAction(event -> quickScan());

        StackPane insignia = new StackPane();
        insignia.setMinSize(96, 94);
        insignia.setPrefSize(96, 94);
        Circle halo = new Circle(37);
        halo.setFill(Color.web("#ffffff", 0.08));
        halo.setStroke(Color.web("#ffd8e7", 0.7));
        halo.setStrokeWidth(1.4);
        StackPane shield = buildLogoMark();
        shield.setScaleX(1.7);
        shield.setScaleY(1.7);
        insignia.getChildren().addAll(halo, shield);
        banner.getChildren().addAll(message, scan, insignia);
        return banner;
    }

    private GridPane buildActionGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(11);
        grid.setVgap(11);
        for (int column = 0; column < 3; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / 3);
            constraints.setHgrow(Priority.ALWAYS);
            constraints.setFillWidth(true);
            grid.getColumnConstraints().add(constraints);
        }
        for (int row = 0; row < 2; row++) {
            RowConstraints constraints = new RowConstraints();
            constraints.setPercentHeight(50);
            constraints.setVgrow(Priority.ALWAYS);
            constraints.setFillHeight(true);
            grid.getRowConstraints().add(constraints);
        }
        grid.setMaxHeight(Double.MAX_VALUE);

        addActionTile(grid, actionTile(
                "⌕", "QUICK SCAN", "Scan Downloads, Temp and Startup",
                "#8e2054", "#ef276c", true, this::quickScan), 0, 0);
        addActionTile(grid, actionTile(
                "▣", "FULL SCAN", "Choose a folder or drive to scan",
                "#3e286e", "#8952d9", false, this::chooseAndRunFullScan), 1, 0);
        addActionTile(grid, actionTile(
                "◉", "REAL-TIME MONITORING", "Manual folder watch; best-effort only",
                "#342867", "#7152d0", false, () -> showPage("Protection")), 2, 0);
        addActionTile(grid, actionTile(
                "◎", "THREAT HISTORY", "Review detections and events",
                "#76204f", "#bd347d", false, () -> showPage("Threats")), 0, 1);
        addActionTile(grid, actionTile(
                "⬡", "QUARANTINE", "Optional isolation of exact matches",
                "#6d285c", "#c23d91", false, () -> showPage("Quarantine")), 1, 1);
        addActionTile(grid, actionTile(
                "⌘", "PROCESS MONITOR", "Inspect newly started processes",
                "#243a82", "#477ce9", false, () -> showPage("Processes")), 2, 1);
        return grid;
    }

    private void addActionTile(GridPane grid, Button tile, int column, int row) {
        tile.setMaxHeight(Double.MAX_VALUE);
        GridPane.setHgrow(tile, Priority.ALWAYS);
        GridPane.setVgrow(tile, Priority.ALWAYS);
        grid.add(tile, column, row);
    }

    private Button actionTile(
            String symbol,
            String title,
            String description,
            String startColor,
            String endColor,
            boolean primary,
            Runnable action) {
        VBox content = new VBox(7);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(12, 8, 12, 8));
        Circle iconCircle = new Circle(21);
        iconCircle.setFill(Color.web("#ffffff", 0.08));
        iconCircle.setStroke(Color.web("#ffffff", 0.72));
        Label icon = styledLabel(symbol, "#fff7fb", 22, true);
        StackPane iconMark = new StackPane(iconCircle, icon);
        Label name = styledLabel(title, "#fff9fc", 14, true);
        Label detail = styledLabel(description, "#f1ddea", 11, false);
        detail.setWrapText(true);
        detail.setAlignment(Pos.CENTER);
        content.getChildren().addAll(iconMark, name, detail);

        Button tile = new Button();
        tile.setGraphic(content);
        tile.setAccessibleText(title + ". " + description);
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setMinHeight(123);
        tile.setPrefHeight(158);
        tile.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, "
                        + endColor + ", " + startColor + ");"
                        + "-fx-background-radius: 13; -fx-padding: 5;"
                        + "-fx-cursor: hand;"
                        + (primary
                                ? "-fx-effect: dropshadow(gaussian, rgba(239,39,108,0.2), 13, 0.08, 0, 4);"
                                : "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0.05, 0, 3);"));
        tile.setOnAction(event -> action.run());
        if (primary || title.equals("FULL SCAN")) {
            registerOperationButton(tile);
        }
        return tile;
    }

    private HBox buildSecurityFacts() {
        HBox facts = new HBox(10);
        facts.setAlignment(Pos.CENTER_LEFT);
        VBox engineFact = factTile("ENGINE", modeValue, CYAN);
        VBox filesFact = factTile("FILES SCANNED", filesValue, "#f6d4eb");
        VBox detectionsFact = factTile("SIGNATURE MATCHES", detectionsValue, "#ff719f");
        VBox suspiciousFact = factTile("SUSPICIOUS INDICATORS", suspiciousValue, "#ffc46a");
        facts.getChildren().addAll(engineFact, filesFact, detectionsFact, suspiciousFact);
        facts.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        return facts;
    }

    private VBox factTile(String caption, Label value, String accent) {
        VBox tile = new VBox(5,
                styledLabel(caption, "#9a93b1", 8, true),
                styledLabel(value.getText(), accent, 13, true));
        value.textProperty().addListener((observable, previous, current) ->
                ((Label) tile.getChildren().get(1)).setText(current));
        tile.setPadding(new Insets(8, 11, 8, 11));
        tile.setStyle(
                "-fx-background-color: #1b182e; -fx-background-radius: 8;"
                        + "-fx-border-color: #332e48; -fx-border-radius: 8;");
        return tile;
    }

    private VBox buildScanPage() {
        VBox content = pageContent();
        VBox quick = card("Quick scan");
        quick.getChildren().addAll(
                styledLabel("Downloads, Temp and Startup", TEXT, 16, true),
                styledLabel(
                        "Scans these current-user locations with the configured local SHA-256 signatures.",
                        MUTED, 12, false));
        Button quickButton = primaryButton("Start quick scan");
        registerOperationButton(quickButton);
        quickButton.setOnAction(event -> quickScan());
        quick.getChildren().add(quickButton);

        VBox full = card("Full scan");
        full.getChildren().addAll(
                styledLabel("Choose a drive or folder", TEXT, 16, true),
                styledLabel(
                        "Recursively scans the selected location. The run can take a long time; Windows permissions may limit which files the engine can read.",
                        MUTED, 12, false));
        Button fullButton = secondaryButton("Choose location and scan");
        registerOperationButton(fullButton);
        fullButton.setOnAction(event -> chooseAndRunFullScan());
        full.getChildren().add(fullButton);

        VBox custom = card("Custom scan");
        custom.getChildren().addAll(
                styledLabel("Select a folder or a single file", TEXT, 16, true),
                styledLabel(
                        "The chosen location is scanned on demand. No files are uploaded or executed.",
                        MUTED, 12, false));
        HBox choices = new HBox(10);
        Button folderButton = secondaryButton("Choose folder");
        registerOperationButton(folderButton);
        folderButton.setOnAction(event -> chooseAndScanFolder());
        Button fileButton = secondaryButton("Choose file");
        registerOperationButton(fileButton);
        fileButton.setOnAction(event -> chooseAndScanFile());
        choices.getChildren().addAll(folderButton, fileButton);
        custom.getChildren().add(choices);

        VBox result = card("Latest scan");
        result.getChildren().addAll(
                statRow("Status", modeValue),
                statRow("Files scanned", filesValue),
                statRow("Signature matches", detectionsValue),
                statRow("Suspicious indicators", suspiciousValue),
                statRow("Highest file risk (not a probability)", riskValue),
                statRow("Selected location", selectedTarget));
        content.getChildren().addAll(quick, full, custom, result);
        return content;
    }

    private VBox buildMonitoringPage(boolean processesOnly) {
        VBox content = pageContent();
        VBox note = card(processesOnly ? "Process monitoring is manual" : "Monitoring is manual");
        note.getChildren().addAll(
                styledLabel(
                        "Monitoring only runs after you start it and while its engine process is active.",
                        TEXT, 16, true),
                styledLabel(
                        "Filesystem events are best-effort. Process monitoring polls for new starts and can miss short-lived processes. Neither mode blocks or terminates processes.",
                        MUTED, 12, false));

        VBox filesystem = card("Folder monitoring");
        filesystem.getChildren().addAll(
                styledLabel("Selected folder", TEXT, 15, true),
                selectedTarget);
        HBox folderActions = new HBox(10);
        Button choose = secondaryButton("Choose folder");
        registerOperationButton(choose);
        choose.setOnAction(event -> chooseMonitorFolder());
        Button watch = primaryButton("Start folder monitoring");
        registerOperationButton(watch);
        watch.setOnAction(event -> startWatching());
        folderActions.getChildren().addAll(choose, watch);
        filesystem.getChildren().add(folderActions);

        VBox processes = card("Process monitoring");
        processes.getChildren().addAll(
                styledLabel("Inspect newly started processes", TEXT, 15, true),
                styledLabel(
                        "Scans accessible process images against the local signature database. Does not terminate a process.",
                        MUTED, 12, false));
        Button monitor = primaryButton("Start process monitoring");
        registerOperationButton(monitor);
        monitor.setOnAction(event -> runProcessMonitor());
        Button tree = secondaryButton("Show process tree snapshot");
        registerOperationButton(tree);
        tree.setOnAction(event -> showProcessTree());
        processes.getChildren().addAll(monitor, tree);

        if (processesOnly) {
            content.getChildren().addAll(note, processes);
        } else {
            content.getChildren().addAll(note, filesystem, processes);
        }
        return content;
    }

    private VBox buildQuarantinePage() {
        VBox content = pageContent();
        VBox quarantine = card("QUARANTINE CONTROLS");
        quarantineEnabled.setStyle("-fx-text-fill: " + TEXT + ";");

        TextField destinationPath = quarantinePath;
        styleTextField(destinationPath);
        destinationPath.setPromptText("Quarantine folder (optional if set in config)");
        HBox.setHgrow(destinationPath, Priority.ALWAYS);
        Button browse = secondaryButton("Browse");
        browse.setOnAction(event -> chooseDirectory(destinationPath));

        HBox destination = new HBox(10, destinationPath, browse);
        Label limitation = styledLabel(
                "Quarantine is off by default. Only exact SHA-256 signature matches are moved; heuristic indicators are never quarantined. Restoring returns the item to its original path and never overwrites an existing file.",
                MUTED, 11, false);
        limitation.setWrapText(true);
        quarantine.getChildren().addAll(
                styledLabel("Off by default; enable only if you want automatic moves", TEXT, 15, true),
                limitation,
                quarantineEnabled,
                destination);

        VBox historyCard = card("Quarantine history");
        quarantineEntries = new ListView<>(quarantinedItems);
        quarantineEntries.setPlaceholder(styledLabel(
                "No quarantine entries loaded.", MUTED, 11, false));
        quarantineEntries.setPrefHeight(250);
        quarantineEntries.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(QuarantineItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.toString());
                setWrapText(true);
                setMaxWidth(Double.MAX_VALUE);
                setStyle(
                        "-fx-background-color: #171426; -fx-text-fill: " + TEXT + ";"
                                + "-fx-padding: 8 4 8 4; -fx-font-size: 10px;");
            }
        });
        Button refresh = secondaryButton("Refresh history");
        registerOperationButton(refresh);
        refresh.setOnAction(event -> refreshQuarantine());
        Button restore = primaryButton("Restore selected item");
        registerOperationButton(restore);
        restore.setOnAction(event -> restoreSelectedQuarantineItem());
        HBox actions = new HBox(10, refresh, restore);
        historyCard.getChildren().addAll(
                styledLabel(
                        "Metadata is stored locally beside isolated files. The engine verifies the stored SHA-256 and refuses to overwrite the original destination.",
                        MUTED, 11, false),
                quarantineEntries,
                actions);
        content.getChildren().addAll(quarantine, historyCard);
        return content;
    }

    private VBox buildActivityPage() {
        VBox content = pageContent();
        VBox outputCard = card("Engine output");
        activity.setEditable(false);
        activity.setWrapText(false);
        activity.setPromptText("Scan or monitoring output appears here.");
        activity.setPrefRowCount(15);
        activity.setStyle(
                "-fx-control-inner-background: #171426; -fx-text-fill: #e1dcef;"
                        + "-fx-font-family: 'Consolas'; -fx-font-size: 12px;"
                        + "-fx-border-color: " + BORDER + ";");
        VBox.setVgrow(activity, Priority.ALWAYS);
        outputCard.getChildren().add(activity);

        VBox historyCard = card("Session events");
        historyCard.getChildren().add(styledLabel(
                "Events are kept in memory for this session; they are not a persistent threat database.",
                MUTED, 10, false));
        ListView<String> list = historyList(10);
        VBox.setVgrow(list, Priority.ALWAYS);
        historyCard.getChildren().add(list);
        content.getChildren().addAll(outputCard, historyCard);
        return content;
    }

    private VBox buildSettingsPage() {
        VBox content = pageContent();
        VBox engineSettings = card("Engine");
        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(12);
        addPathField(fields, 0, "Engine executable", enginePath, "Select engine", "*.exe",
                "Select Sentinel AV engine");
        addPathField(fields, 1, "Configuration file (optional)", configPath,
                "Select config", "*.ini", "Select Sentinel AV configuration");
        addPathField(fields, 2, "Signature database", signaturesPath,
                "Select signatures", "*.txt", "Select signature database");
        engineSettings.getChildren().addAll(
                fields,
                styledLabel(
                        "The example signature database contains only the harmless `abc` test vector.",
                        MUTED, 11, false));

        content.getChildren().add(engineSettings);
        return content;
    }

    private void addPathField(
            GridPane grid,
            int row,
            String caption,
            TextField field,
            String buttonText,
            String extension,
            String chooserTitle) {
        grid.add(styledLabel(caption, TEXT, 12, true), 0, row);
        styleTextField(field);
        field.setPrefColumnCount(32);
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(field, 1, row);
        Button browse = secondaryButton(buttonText);
        browse.setOnAction(event -> chooseFile(field, extension, chooserTitle));
        grid.add(browse, 2, row);
    }

    private VBox card(String title) {
        VBox box = new VBox(14);
        box.setPadding(new Insets(18));
        box.setStyle(
                "-fx-background-color: " + CARD + "; -fx-background-radius: 11;"
                        + "-fx-border-color: " + BORDER + "; -fx-border-radius: 11;");
        box.getChildren().add(styledLabel(title, TEXT, 15, true));
        return box;
    }

    private VBox pageContent() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(24, 32, 28, 32));
        content.setFillWidth(true);
        return content;
    }

    private HBox statRow(String caption, Label value) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = styledLabel(caption, MUTED, 12, false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        value.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        row.getChildren().addAll(label, spacer, value);
        return row;
    }

    private ListView<String> historyList(int preferredRows) {
        ListView<String> list = new ListView<>(history);
        list.setPrefHeight(preferredRows * 34);
        list.setMinWidth(0);
        list.setMaxWidth(Double.MAX_VALUE);
        list.setStyle(
                "-fx-background-color: #171426; -fx-control-inner-background: #171426;"
                        + "-fx-background-insets: 0; -fx-padding: 1;"
                        + "-fx-border-color: " + BORDER + "; -fx-text-fill: " + TEXT + ";");
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setWrapText(true);
                setMaxWidth(Double.MAX_VALUE);
                setStyle(
                        "-fx-background-color: #171426; -fx-text-fill: " + TEXT + ";"
                                + "-fx-padding: 8 4 8 4; -fx-font-size: 10px;");
            }
        });
        return list;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.setMinHeight(40);
        button.setPadding(new Insets(0, 17, 0, 17));
        button.setStyle(
                "-fx-background-color: linear-gradient(to right, " + PINK + ", #c52868);"
                        + "-fx-text-fill: #fff7fb;"
                        + "-fx-font-size: 12px; -fx-font-weight: bold;"
                        + "-fx-background-radius: 7;"
                        + "-fx-effect: dropshadow(gaussian, rgba(229,31,95,0.25), 10, 0, 0, 2);");
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.setMinHeight(38);
        button.setPadding(new Insets(0, 14, 0, 14));
        button.setStyle(
                "-fx-background-color: #28233e; -fx-text-fill: " + TEXT + ";"
                        + "-fx-font-size: 12px; -fx-font-weight: bold;"
                        + "-fx-border-color: #46405d; -fx-border-radius: 7;"
                        + "-fx-background-radius: 7;");
        return button;
    }

    private void registerOperationButton(Button button) {
        operationButtons.add(button);
    }

    private Label styledLabel(String text, String color, int size, boolean bold) {
        Label label = new Label(text);
        label.setStyle(
                "-fx-text-fill: " + color + "; -fx-font-size: " + size + "px;"
                        + (bold ? " -fx-font-weight: bold;" : ""));
        return label;
    }

    private String navStyle(boolean selected) {
        return "-fx-background-color: " + (selected ? NAVY_LIGHT : "transparent") + ";"
                + "-fx-text-fill: " + (selected ? "#ff75a5" : "#c1bad2") + ";"
                + "-fx-font-size: 12px; -fx-font-weight: "
                + (selected ? "bold" : "normal") + "; -fx-background-radius: 7;"
                + (selected
                        ? "-fx-border-color: transparent transparent transparent " + PINK
                                + "; -fx-border-width: 0 0 0 3;"
                        : "");
    }

    private void styleTextField(TextField field) {
        field.setStyle(
                "-fx-background-color: #171426; -fx-text-fill: " + TEXT + ";"
                        + "-fx-prompt-text-fill: #817a98; -fx-background-radius: 5;"
                        + "-fx-border-color: #403854; -fx-border-radius: 5;");
    }

    private void quickScan() {
        List<String> arguments = baseCommand("quick-scan", null);
        if (arguments == null || !appendQuarantineOption(arguments)) {
            return;
        }
        selectedTarget.setText("Downloads + Temp + Startup (current user)");
        filesValue.setText("—");
        detectionsValue.setText("0");
        suspiciousValue.setText("0");
        highestRiskScoreObserved = 0;
        riskValue.setText("No indicators observed (not a safety verdict)");
        startEngine(arguments, "Quick scan");
        showPage("Scanner");
    }

    private void chooseAndRunFullScan() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a drive or folder for a full scan");
        Path root = Path.of(System.getProperty("user.home")).getRoot();
        if (root != null && Files.isDirectory(root)) {
            chooser.setInitialDirectory(root.toFile());
        }
        File selected = chooser.showDialog(stage);
        if (selected == null) {
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm full scan");
        confirmation.setHeaderText("Scan the selected location recursively?");
        confirmation.setContentText(
                selected.getAbsolutePath()
                        + "\n\nThis may take a long time. The engine can only scan files Windows allows it to read.");
        confirmation.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        confirmation.showAndWait()
                .filter(ButtonType.OK::equals)
                .ifPresent(button -> startPathCommand("scan", selected.toPath()));
    }

    private void chooseAndScanFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a folder to scan");
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            startPathCommand("scan", selected.toPath());
        }
    }

    private void chooseAndScanFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a file to scan");
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            startPathCommand("scan", selected.toPath());
        }
    }

    private void chooseMonitorFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a folder to monitor");
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            selectedTarget.setText(selected.getAbsolutePath());
        }
    }

    private void startWatching() {
        String target = selectedTarget.getText();
        if ("No folder selected".equals(target) || !Files.isDirectory(Path.of(target))) {
            addHistory("ERROR", "Choose an existing folder before starting monitoring.");
            return;
        }
        startPathCommand("watch", Path.of(target));
    }

    private void startPathCommand(String command, Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        List<String> arguments = baseCommand(command, normalized.toString());
        if (arguments == null || !appendQuarantineOption(arguments)) {
            return;
        }
        selectedTarget.setText(normalized.toString());
        filesValue.setText("—");
        detectionsValue.setText("0");
        suspiciousValue.setText("0");
        highestRiskScoreObserved = 0;
        riskValue.setText("No indicators observed (not a safety verdict)");
        startEngine(arguments, command.equals("scan") ? "Scanning" : "Monitoring");
        if (command.equals("scan")) {
            showPage("Scan");
        } else {
            showPage("Monitoring");
        }
    }

    private boolean appendQuarantineOption(List<String> arguments) {
        if (quarantineEnabled.isSelected()) {
            String destination = quarantinePath.getText().trim();
            if (destination.isEmpty() && configPath.getText().isBlank()) {
                addHistory("ERROR", "Set a quarantine folder in Settings or turn quarantine off.");
                showPage("Settings");
                return false;
            }
            arguments.add("--quarantine");
            if (!destination.isEmpty()) {
                try {
                    arguments.add(Path.of(destination).toAbsolutePath().normalize().toString());
                } catch (RuntimeException error) {
                    addHistory("ERROR", "Invalid quarantine path: " + error.getMessage());
                    showPage("Settings");
                    return false;
                }
            }
        }
        return true;
    }

    private void runProcessMonitor() {
        List<String> arguments = baseCommand("processes", null);
        if (arguments != null) {
            startEngine(arguments, "Monitoring processes");
        }
    }

    private void showProcessTree() {
        List<String> arguments = baseCommand("process-tree", null);
        if (arguments != null) {
            startEngine(arguments, "Process tree snapshot");
            showPage("Threats");
        }
    }

    private void refreshQuarantine() {
        List<String> arguments = baseCommand("quarantine", null);
        if (arguments == null) {
            return;
        }
        arguments.add(2, "list");
        if (!appendQuarantineDirectory(arguments)) {
            return;
        }
        quarantinedItems.clear();
        startEngine(arguments, "Loading quarantine history");
    }

    private void restoreSelectedQuarantineItem() {
        QuarantineItem selected =
                quarantineEntries == null ? null : quarantineEntries.getSelectionModel().getSelectedItem();
        if (selected == null) {
            addHistory("ERROR", "Select a quarantine entry before restoring.");
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Restore quarantined item");
        confirmation.setHeaderText("Restore the selected file to its original path?");
        confirmation.setContentText(
                selected.originalPath()
                        + "\n\nThe operation verifies the stored SHA-256 and refuses to overwrite an existing file.");
        confirmation.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }

        List<String> arguments = baseCommand("quarantine", null);
        if (arguments == null) {
            return;
        }
        arguments.add(2, "restore");
        arguments.add(3, selected.id());
        if (!appendQuarantineDirectory(arguments)) {
            return;
        }
        startEngine(arguments, "Restoring quarantine");
    }

    private boolean appendQuarantineDirectory(List<String> arguments) {
        String configuredDirectory = quarantinePath.getText().trim();
        if (!configuredDirectory.isEmpty()) {
            try {
                arguments.add("--quarantine-dir");
                arguments.add(Path.of(configuredDirectory).toAbsolutePath().normalize().toString());
            } catch (RuntimeException error) {
                addHistory("ERROR", "Invalid quarantine path: " + error.getMessage());
                return false;
            }
        }
        return true;
    }

    private List<String> baseCommand(String command, String target) {
        Path executable = existingPath(enginePath.getText(), "Choose the Sentinel AV engine in Settings.");
        if (executable == null) {
            showPage("Settings");
            return null;
        }
        Path signatures = existingPath(
                signaturesPath.getText(), "Choose a signature database in Settings.");
        if (signatures == null) {
            showPage("Settings");
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
                    configPath.getText(), "Choose a valid engine configuration file in Settings.");
            if (config == null) {
                showPage("Settings");
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

    private void chooseFile(TextField field, String extension, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(extension + " files", extension));
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            field.setText(selected.getAbsolutePath());
        }
    }

    private void chooseDirectory(TextField field) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose quarantine folder");
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            field.setText(selected.getAbsolutePath());
        }
    }

    private void startEngine(List<String> arguments, String mode) {
        stopRequestedByUser = false;
        activity.clear();
        try {
            engine.start(
                    arguments,
                    this::receiveOutput,
                    (exitCode, failure) -> Platform.runLater(() -> {
                        setBusy(false);
                        setEngineStatus("Idle");
                        if (failure != null) {
                            modeValue.setText("Error");
                            addHistory("ERROR", "Engine communication failed: " + failure.getMessage());
                        } else if (stopRequestedByUser) {
                            modeValue.setText("Stopped");
                            addHistory("INFO", "Engine operation stopped.");
                        } else if (exitCode == 0) {
                            modeValue.setText("Complete");
                            addHistory("INFO", "Engine operation completed.");
                            if ("Restoring quarantine".equals(mode)) {
                                refreshQuarantine();
                            }
                        } else {
                            modeValue.setText("Error");
                            addHistory("ERROR", "Engine exited with code " + exitCode + ".");
                        }
                    }));
            setBusy(true);
            modeValue.setText(mode);
            setEngineStatus("Running");
            appendActivity("$ " + String.join(" ", arguments));
            addHistory("INFO", mode + " started.");
        } catch (Exception error) {
            modeValue.setText("Error");
            setEngineStatus("Unavailable");
            addHistory("ERROR", "Could not start engine: " + error.getMessage());
        }
    }

    private void setEngineStatus(String status) {
        engineStatus.setText(status);
        String color = switch (status) {
            case "Running" -> GREEN;
            case "Unavailable" -> "#fb7185";
            default -> MUTED;
        };
        statusDot.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        engineStatus.setStyle(
                "-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");
    }

    private void updateHeroStatus(String mode) {
        switch (mode) {
            case "Scanning" -> {
                heroStatus.setText("SCAN IN PROGRESS");
                statusDescription.setText("Sentinel is checking the selected location against local signatures.");
            }
            case "Quick scan" -> {
                heroStatus.setText("QUICK SCAN IN PROGRESS");
                statusDescription.setText("Checking the current user's Downloads, temporary, and Startup folders.");
            }
            case "Monitoring", "Monitoring processes" -> {
                heroStatus.setText("MONITORING RUNNING");
                statusDescription.setText("User-started monitoring is active; it is best-effort and may miss events.");
            }
            case "Process tree snapshot" -> {
                heroStatus.setText("PROCESS SNAPSHOT");
                statusDescription.setText("A point-in-time process ancestry view is being collected.");
            }
            case "Loading quarantine history" -> {
                heroStatus.setText("LOADING QUARANTINE");
                statusDescription.setText("Reading local quarantine metadata.");
            }
            case "Restoring quarantine" -> {
                heroStatus.setText("RESTORING FILE");
                statusDescription.setText("Restoring the selected file only if the original path is still free.");
            }
            case "Complete" -> {
                heroStatus.setText("OPERATION COMPLETE");
                statusDescription.setText("Review the operation output and local history.");
            }
            case "Stopped", "Stopping" -> {
                heroStatus.setText("OPERATION STOPPED");
                statusDescription.setText("No background protection is active.");
            }
            case "Error" -> {
                heroStatus.setText("ENGINE NEEDS ATTENTION");
                statusDescription.setText("Check engine output and settings before running another scan.");
            }
            default -> {
                heroStatus.setText("YOUR DEVICE IS READY");
                statusDescription.setText(
                        "On-demand scans are available. Background protection is not active.");
            }
        }
    }

    private void receiveOutput(String line) {
        Platform.runLater(() -> {
            appendActivity(line);
            Matcher riskMatcher = FILE_RISK.matcher(line);
            if (riskMatcher.find()) {
                int score = Integer.parseInt(riskMatcher.group(1));
                if (score > highestRiskScoreObserved) {
                    highestRiskScoreObserved = score;
                    riskValue.setText(
                            score + " / 100 — "
                                    + riskMatcher.group(2).trim() + " — "
                                    + riskMatcher.group(3).trim());
                }
                addHistory(
                        "RISK",
                        riskMatcher.group(2).trim() + " "
                                + riskMatcher.group(1) + "/100 — "
                                + riskMatcher.group(3).trim());
            }
            if (line.startsWith("QUARANTINE_ENTRY\t")) {
                try {
                    quarantinedItems.add(parseQuarantineEntry(line));
                } catch (IllegalArgumentException error) {
                    addHistory("ERROR", "Invalid quarantine history entry: " + error.getMessage());
                }
            } else if (line.equals("QUARANTINE_EMPTY")) {
                quarantinedItems.clear();
            } else if (line.contains("[DETECTED]")) {
                detectionsValue.setText(
                        Integer.toString(Integer.parseInt(detectionsValue.getText()) + 1));
                addHistory("DETECTION", line);
            } else if (line.contains("[SUSPICIOUS]")) {
                suspiciousValue.setText(
                        Integer.toString(Integer.parseInt(suspiciousValue.getText()) + 1));
                addHistory("SUSPICIOUS", line);
            } else if (line.contains("[PROCESS START]")) {
                addHistory("PROCESS", line);
            } else if (line.startsWith("[PROCESS TREE]")) {
                addHistory("PROCESS TREE", line);
            } else if (line.startsWith("[RANSOMWARE-LIKE ACTIVITY]")) {
                addHistory("RANSOMWARE ALERT", line);
            } else if (line.startsWith("[ERROR]") || line.startsWith("[FATAL]")) {
                addHistory("ERROR", line);
            } else {
                Matcher quickMatcher = QUICK_SCAN_SUMMARY.matcher(line);
                if (quickMatcher.find()) {
                    filesValue.setText(quickMatcher.group(2));
                    detectionsValue.setText(quickMatcher.group(3));
                    suspiciousValue.setText(quickMatcher.group(4));
                    int score = Integer.parseInt(quickMatcher.group(7));
                    if (score == 0) {
                        riskValue.setText("No indicators observed (not a safety verdict)");
                    } else if (score >= highestRiskScoreObserved) {
                        highestRiskScoreObserved = score;
                        riskValue.setText(
                                score + " / 100 — " + quickMatcher.group(8));
                    }
                    addHistory(
                            "QUICK SCAN",
                            quickMatcher.group(1) + " location(s), "
                                    + quickMatcher.group(2) + " file(s), "
                                    + quickMatcher.group(5) + " skipped, "
                                    + quickMatcher.group(6) + " error(s).");
                } else {
                    Matcher matcher = SCAN_SUMMARY.matcher(line);
                    if (matcher.find()) {
                        filesValue.setText(matcher.group(1));
                        detectionsValue.setText(matcher.group(2));
                        suspiciousValue.setText(matcher.group(3));
                        addHistory("SCAN", line);
                    }
                }
            }
        });
    }

    private static QuarantineItem parseQuarantineEntry(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 6 || !"QUARANTINE_ENTRY".equals(fields[0])) {
            throw new IllegalArgumentException("unexpected record shape");
        }
        return new QuarantineItem(
                decodeQuarantineField(fields[1]),
                decodeQuarantineField(fields[2]),
                decodeQuarantineField(fields[3]),
                decodeQuarantineField(fields[4]),
                decodeQuarantineField(fields[5]));
    }

    private static String decodeQuarantineField(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
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
            stopRequestedByUser = true;
            engine.stop();
            modeValue.setText("Stopping");
            addHistory("INFO", "Stop requested for active engine process.");
        }
    }

    private void setBusy(boolean busy) {
        stopButton.setDisable(!busy);
        operationButtons.forEach(button -> button.setDisable(busy));
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
        return Path.of("..", "build", "sentinel-av.exe")
                .toAbsolutePath().normalize().toString();
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

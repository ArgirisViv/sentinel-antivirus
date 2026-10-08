package com.sentinelav.desktop;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SentinelDashboard extends Application {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String NO_FOLDER = "No folder selected";
    private static final String NO_INDICATORS = "No indicators observed (not a safety verdict)";
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
    private static final Pattern LEADING_TAG = Pattern.compile("^.*?\\[[A-Z -]+\\]\\s*");

    private static final Map<String, String> ICONS = Map.ofEntries(
            Map.entry("home", "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10"),
            Map.entry("shield", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"),
            Map.entry("search", "M21 21l-4.35-4.35 M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z"),
            Map.entry("alert", "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z M12 9v4 M12 17h.01"),
            Map.entry("archive", "M21 8v13H3V8 M1 3h22v5H1z M10 12h4"),
            Map.entry("cpu", "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M9 9h6v6H9z M9 1v3 M15 1v3 M9 20v3 M15 20v3 M20 9h3 M20 14h3 M1 9h3 M1 14h3"),
            Map.entry("sliders", "M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6"),
            Map.entry("play", "M5 3l14 9-14 9z"),
            Map.entry("stop", "M6 6h12v12H6z"),
            Map.entry("folder", "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"),
            Map.entry("file", "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6"),
            Map.entry("drive", "M22 12H2 M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z M6 16h.01 M10 16h.01"),
            Map.entry("zap", "M13 2L3 14h9l-1 8 10-12h-9z"),
            Map.entry("eye", "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"),
            Map.entry("refresh", "M23 4v6h-6 M1 20v-6h6 M3.51 9a9 9 0 0 1 14.85-3.36L23 10 M1 14l4.64 4.36A9 9 0 0 0 20.49 15"),
            Map.entry("undo", "M3 7v6h6 M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"));

    private enum Kind {
        INFO("INFO", "chip-info"),
        SCAN("SCAN", "chip-ok"),
        DETECTION("DETECTED", "chip-danger"),
        SUSPICIOUS("SUSPICIOUS", "chip-warn"),
        PROCESS("PROCESS", "chip-info"),
        ALERT("ALERT", "chip-danger"),
        ERROR("ERROR", "chip-danger");

        final String label;
        final String css;

        Kind(String label, String css) {
            this.label = label;
            this.css = css;
        }
    }

    private record FeedEvent(String time, Kind kind, String message) { }

    private record QuarantineItem(
            String id,
            String sha256,
            String threat,
            String timestamp,
            String originalPath) { }

    private final EngineClient engine = new EngineClient();
    private final ObservableList<FeedEvent> feed = FXCollections.observableArrayList();
    private final FilteredList<FeedEvent> filteredFeed = new FilteredList<>(feed, e -> true);
    private final ObservableList<QuarantineItem> quarantinedItems =
            FXCollections.observableArrayList();
    private final TextField enginePath = new TextField(defaultEnginePath());
    private final TextField configPath = new TextField(defaultConfigPath());
    private final TextField signaturesPath = new TextField(defaultSignaturesPath());
    private final TextField quarantinePath = new TextField();
    private final CheckBox quarantineEnabled = new CheckBox(
            "Move detected signature or EICAR test files to quarantine after a scan");
    private final TextArea console = new TextArea();

    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final IntegerProperty filesScanned = new SimpleIntegerProperty(-1);
    private final IntegerProperty detections = new SimpleIntegerProperty(0);
    private final IntegerProperty suspicious = new SimpleIntegerProperty(0);
    private final IntegerProperty highestRisk = new SimpleIntegerProperty(-1);
    private final StringProperty riskText = new SimpleStringProperty("No scan has been run yet");
    private final StringProperty mode = new SimpleStringProperty("Ready");
    private final StringProperty engineStatus = new SimpleStringProperty("Idle");
    private final StringProperty headline = new SimpleStringProperty();
    private final StringProperty subline = new SimpleStringProperty();
    private final StringProperty chipText = new SimpleStringProperty("READY");
    private final StringProperty selectedTarget = new SimpleStringProperty(NO_FOLDER);
    private final StringProperty elapsed = new SimpleStringProperty("00:00");

    private final ShieldGauge gauge = new ShieldGauge(236);
    private final Label heroChip = new Label();
    private final StackPane pageHost = new StackPane();
    private final Label pageTitle = new Label();
    private final Label pageSubtitle = new Label();
    private final VBox toastLayer = new VBox(10);
    private final Map<String, Node> pageCache = new HashMap<>();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Map<String, Region> navIndicators = new HashMap<>();
    private final Map<String, String[]> pageMeta = new LinkedHashMap<>();
    private final Timeline elapsedTimer = new Timeline();

    private ListView<QuarantineItem> quarantineEntries;
    private Animation pageTransition;
    private Stage stage;
    private StartupSplash startupSplash;
    private String css;
    private String currentPage;
    private long startedAtNanos;
    private boolean stopRequestedByUser;
    private int highestRiskScoreObserved;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        Image logoImage = new Image(getClass().getResourceAsStream("logo.png"));
        stage.getIcons().add(logoImage);
        css = getClass().getResource("sentinel.css").toExternalForm();
        stage.setTitle("Sentinel AV");
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        startupSplash = new StartupSplash();
        Scene splashScene = new Scene(startupSplash, 1100, 720);
        splashScene.getStylesheets().add(css);
        stage.setScene(splashScene);
        stage.setOnCloseRequest(e -> {
            startupSplash.stop();
            engine.close();
        });
        stage.show();
        startupSplash.play(this::showDashboard);
    }

    private void showDashboard() {
        pageMeta.put("Dashboard", new String[] {"Dashboard", "Overview of scan status and recent activity", "home"});
        pageMeta.put("Scanner", new String[] {"Scanner", "Run on-demand scans against local signatures and heuristics", "search"});
        pageMeta.put("Protection", new String[] {"Monitoring", "User-started, best-effort folder and process monitoring", "eye"});
        pageMeta.put("Threats", new String[] {"Activity", "Findings, events and raw engine output", "alert"});
        pageMeta.put("Quarantine", new String[] {"Quarantine", "Review and restore quarantined files", "archive"});
        pageMeta.put("Settings", new String[] {"Settings", "Engine, signature and quarantine locations", "sliders"});

        console.setEditable(false);
        console.setWrapText(false);
        quarantineEnabled.setWrapText(true);
        quarantinePath.setPromptText("Optional: engine default when empty");

        elapsedTimer.getKeyFrames().add(new KeyFrame(Duration.seconds(1), e -> tickElapsed()));
        elapsedTimer.setCycleCount(Animation.INDEFINITE);

        mode.addListener((o, a, b) -> refreshHero());
        detections.addListener((o, a, b) -> refreshHero());
        refreshHero();

        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setLeft(buildSidebar());
        shell.setCenter(buildMain());

        toastLayer.setAlignment(Pos.BOTTOM_RIGHT);
        toastLayer.setPickOnBounds(false);
        toastLayer.setMouseTransparent(true);
        toastLayer.setPadding(new Insets(0, 28, 26, 0));
        StackPane root = new StackPane(new AnimatedBackground(), shell, toastLayer);

        Scene scene = new Scene(root, 1360, 860);
        scene.getStylesheets().add(css);
        HoverLift.install(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);
        showPage("Dashboard");
    }

    // ------------------------------------------------------------------ shell

    private Node buildSidebar() {
        StackPane logoBadge = new StackPane(BrandLogo.canvas(34));
        logoBadge.setMinSize(38, 38);
        logoBadge.setPrefSize(38, 38);
        logoBadge.setMaxSize(38, 38);
        DropShadow logoGlow = new DropShadow(14, Color.web("#2dd4bf", 0.55));
        logoBadge.setEffect(logoGlow);
        Timeline logoPulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(logoGlow.radiusProperty(), 8)),
                new KeyFrame(Duration.seconds(2.2), new KeyValue(logoGlow.radiusProperty(), 22)));
        logoPulse.setAutoReverse(true);
        logoPulse.setCycleCount(Animation.INDEFINITE);
        logoPulse.play();

        Label title = new Label("Sentinel AV");
        title.getStyleClass().add("brand-title");
        Label sub = new Label("SECURITY CONSOLE");
        sub.getStyleClass().add("brand-sub");
        HBox brand = new HBox(12, logoBadge, new VBox(1, title, sub));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(4, 8, 22, 8));

        Label caption = new Label("WORKSPACE");
        caption.getStyleClass().add("nav-caption");
        caption.setPadding(new Insets(0, 0, 6, 14));

        VBox nav = new VBox(3, brand, caption);
        for (Map.Entry<String, String[]> entry : pageMeta.entrySet()) {
            String key = entry.getKey();
            Button button = new Button(entry.getValue()[0]);
            button.setGraphic(icon(entry.getValue()[2], 18));
            button.setGraphicTextGap(12);
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(e -> showPage(key));
            TranslateTransition slide = new TranslateTransition(Duration.millis(140), button);
            button.hoverProperty().addListener((o, a, hovered) -> {
                slide.stop();
                slide.setToX(hovered ? 5 : 0);
                slide.play();
            });
            Region indicator = new Region();
            indicator.getStyleClass().add("nav-indicator");
            indicator.setPrefSize(3, 18);
            indicator.setMaxSize(3, 18);
            indicator.setOpacity(0);
            indicator.setMouseTransparent(true);
            StackPane.setAlignment(indicator, Pos.CENTER_LEFT);
            navButtons.put(key, button);
            navIndicators.put(key, indicator);
            nav.getChildren().add(new StackPane(button, indicator));
            if (key.equals("Threats")) {
                nav.getChildren().add(spacer(8));
            }
        }

        Label cardTitle = new Label("LOCAL ENGINE");
        cardTitle.getStyleClass().add("side-card-title");
        Label cardText = new Label("On-demand scanning only. Real-time protection is not active.");
        cardText.getStyleClass().add("side-card-text");
        cardText.setWrapText(true);
        VBox sideCard = new VBox(6, cardTitle, cardText);
        sideCard.getStyleClass().add("side-card");
        sideCard.setPadding(new Insets(14));

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        VBox sidebar = new VBox(nav, grow, sideCard);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(22, 14, 18, 14));
        sidebar.setPrefWidth(236);
        sidebar.setMinWidth(236);
        sidebar.setMaxWidth(236);
        return sidebar;
    }

    private Node buildMain() {
        pageTitle.getStyleClass().add("page-title");
        pageSubtitle.getStyleClass().add("page-subtitle");
        VBox titles = new VBox(2, pageTitle, pageSubtitle);

        Label elapsedLabel = new Label();
        elapsedLabel.textProperty().bind(elapsed);
        HBox elapsedPill = pill(new Label("Elapsed"), elapsedLabel);
        elapsedPill.visibleProperty().bind(busy);
        elapsedPill.managedProperty().bind(busy);

        Circle dot = new Circle(4);
        dot.getStyleClass().add("pill-dot");
        Label status = new Label();
        status.textProperty().bind(engineStatus);
        HBox enginePill = new HBox(8, dot, status);
        enginePill.getStyleClass().add("pill");
        enginePill.setAlignment(Pos.CENTER_LEFT);
        FadeTransition pulse = new FadeTransition(Duration.millis(900), dot);
        pulse.setFromValue(1);
        pulse.setToValue(0.25);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        engineStatus.addListener((o, a, b) -> {
            dot.getStyleClass().removeAll("running", "error");
            pulse.stop();
            dot.setOpacity(1);
            if ("Running".equals(b)) {
                dot.getStyleClass().add("running");
                pulse.play();
            } else if ("Unavailable".equals(b)) {
                dot.getStyleClass().add("error");
            }
        });

        Button stop = button("Stop", "stop", "btn-danger");
        stop.setOnAction(e -> stopEngine());
        stop.visibleProperty().bind(busy);
        stop.managedProperty().bind(busy);

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox topbar = new HBox(12, titles, grow, elapsedPill, enginePill, stop);
        topbar.getStyleClass().add("topbar");
        topbar.setAlignment(Pos.CENTER_LEFT);

        pageHost.getStyleClass().add("page-host");
        VBox.setVgrow(pageHost, Priority.ALWAYS);
        VBox main = new VBox(topbar, pageHost);
        main.getStyleClass().add("main");
        return main;
    }

    private void showPage(String name) {
        if (name.equals(currentPage)) {
            return;
        }
        currentPage = name;
        String[] meta = pageMeta.get(name);
        pageTitle.setText(meta[0]);
        pageSubtitle.setText(meta[1]);
        navButtons.forEach((key, button) -> {
            boolean selected = key.equals(name);
            button.getStyleClass().remove("selected");
            if (selected) {
                button.getStyleClass().add("selected");
            }
            FadeTransition fade = new FadeTransition(Duration.millis(180), navIndicators.get(key));
            fade.setToValue(selected ? 1 : 0);
            fade.play();
        });

        Node page = pageCache.computeIfAbsent(name, this::buildPage);
        if (pageTransition != null) {
            pageTransition.stop();
        }
        pageHost.getChildren().setAll(page);
        page.setOpacity(0);
        page.setTranslateY(12);
        FadeTransition fade = new FadeTransition(Duration.millis(220), page);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), page);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        pageTransition = new ParallelTransition(fade, slide);
        pageTransition.play();
    }

    private Node buildPage(String name) {
        Supplier<Node> content = switch (name) {
            case "Scanner" -> this::buildScannerPage;
            case "Protection" -> this::buildProtectionPage;
            case "Threats" -> this::buildActivityPage;
            case "Quarantine" -> this::buildQuarantinePage;
            case "Settings" -> this::buildSettingsPage;
            default -> this::buildDashboardPage;
        };
        VBox body = (VBox) content.get();
        body.setPadding(new Insets(26, 32, 32, 32));
        body.setMaxWidth(1180);
        StackPane holder = new StackPane(body);
        holder.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(holder);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    // ------------------------------------------------------------------ pages

    private Node buildDashboardPage() {
        heroChip.getStyleClass().add("chip");
        heroChip.textProperty().bind(chipText);
        Label headlineLabel = new Label();
        headlineLabel.getStyleClass().add("hero-headline");
        headlineLabel.textProperty().bind(headline);
        Label sublineLabel = new Label();
        sublineLabel.getStyleClass().add("hero-sub");
        sublineLabel.setWrapText(true);
        sublineLabel.textProperty().bind(subline);
        Label meta = new Label();
        meta.getStyleClass().add("hero-meta");
        meta.textProperty().bind(selectedTarget.map(t -> NO_FOLDER.equals(t)
                ? "Signature database and heuristics run locally. Nothing is uploaded."
                : "Target: " + t));
        meta.setWrapText(true);

        ProgressBar progress = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.visibleProperty().bind(busy);

        Button quick = button("Quick scan", "zap", "btn-primary");
        quick.setOnAction(e -> quickScan());
        Button folder = button("Scan folder", "folder", "btn-secondary");
        folder.setOnAction(e -> chooseAndScanFolder());
        Button file = button("Scan file", "file", "btn-secondary");
        file.setOnAction(e -> chooseAndScanFile());
        for (Button b : List.of(quick, folder, file)) {
            b.disableProperty().bind(busy);
        }
        HBox actions = new HBox(10, quick, folder, file);

        VBox heroText = new VBox(12, heroChip, headlineLabel, sublineLabel, progress, actions, meta);
        heroText.setAlignment(Pos.CENTER_LEFT);
        heroChip.setMaxWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(heroText, Priority.ALWAYS);
        HBox hero = new HBox(34, gauge, heroText);
        hero.getStyleClass().add("hero");
        hero.setAlignment(Pos.CENTER_LEFT);

        GridPane stats = grid(4, 16,
                statCard("Files scanned", filesScanned, null, "last run"),
                statCard("Signature matches", detections, "accent-red", "exact SHA-256"),
                statCard("Suspicious", suspicious, "accent-amber", "heuristic indicators"),
                riskCard());

        VBox actionsCard = new VBox(12,
                cardHeader("Quick actions", "Common on-demand operations"),
                tile("zap", "badge", "Quick scan", "Downloads, Temp and Startup folders", this::quickScan),
                tile("drive", "badge-blue", "Full scan", "Pick a drive or folder, scanned recursively", this::chooseAndRunFullScan),
                tile("cpu", "badge-amber", "Process snapshot", "Collect the current process tree", this::showProcessTree));
        actionsCard.getStyleClass().add("card");

        Button viewAll = button("View all", null, "btn-ghost");
        viewAll.setOnAction(e -> showPage("Threats"));
        HBox recentHeader = new HBox(cardHeader("Recent activity", "Latest engine events"), spacerH(), viewAll);
        recentHeader.setAlignment(Pos.CENTER_LEFT);
        ListView<FeedEvent> recent = eventList(feed, "No activity yet. Run a scan to get started.");
        recent.setPrefHeight(300);
        VBox recentCard = new VBox(10, recentHeader, recent);
        recentCard.getStyleClass().add("card");

        GridPane lower = new GridPane();
        lower.setHgap(16);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(42);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(58);
        lower.getColumnConstraints().addAll(left, right);
        lower.add(actionsCard, 0, 0);
        lower.add(recentCard, 1, 0);
        return new VBox(18, hero, stats, lower);

    }

    private Node buildScannerPage() {
        GridPane actions = grid(3, 16,
                scanCard("zap", "badge", "Quick scan",
                        "Checks the current user's Downloads, temporary and Startup folders.",
                        "Run quick scan", this::quickScan),
                scanCard("drive", "badge-blue", "Full scan",
                        "Recursively scans a chosen drive or folder. Can take a long time.",
                        "Choose location", this::chooseAndRunFullScan),
                scanCard("folder", "badge-amber", "Custom scan",
                        "Scan one folder or one file of your choice.",
                        "Choose folder", this::chooseAndScanFolder));
        Button fileButton = button("Scan a single file", "file", "btn-secondary");
        fileButton.setOnAction(e -> chooseAndScanFile());
        fileButton.disableProperty().bind(busy);

        GridPane stats = grid(3, 16,
                statCard("Files scanned", filesScanned, null, "last run"),
                statCard("Signature matches", detections, "accent-red", "exact SHA-256"),
                statCard("Suspicious", suspicious, "accent-amber", "heuristic indicators"));

        Label risk = new Label();
        risk.getStyleClass().add("card-body");
        risk.setWrapText(true);
        risk.textProperty().bind(riskText);
        FilteredList<FeedEvent> findings = new FilteredList<>(feed,
                e -> e.kind() != Kind.INFO);
        ListView<FeedEvent> list = eventList(findings, "No findings yet.");
        list.setPrefHeight(280);
        VBox findingsCard = new VBox(10,
                new HBox(cardHeader("Findings", "Highest observed indicator"), spacerH(), fileButton),
                risk, list);
        findingsCard.getStyleClass().add("card");
        return new VBox(18, actions, stats, findingsCard);
    }

    private Node buildProtectionPage() {
        Label target = new Label();
        target.getStyleClass().add("card-body");
        target.setWrapText(true);
        target.textProperty().bind(selectedTarget);
        Button choose = button("Choose folder", "folder", "btn-secondary");
        choose.setOnAction(e -> chooseMonitorFolder());
        Button start = button("Start monitoring", "play", "btn-primary");
        start.setOnAction(e -> startWatching());
        choose.disableProperty().bind(busy);
        start.disableProperty().bind(busy);
        VBox folderCard = new VBox(12,
                cardTitleRow("eye", "badge", "Folder monitoring",
                        "Watches a folder you choose and scans files that appear or change."),
                target, new HBox(10, choose, start));
        folderCard.getStyleClass().add("card");

        Button processes = button("Start process monitor", "cpu", "btn-primary");
        processes.setOnAction(e -> runProcessMonitor());
        Button tree = button("Process tree snapshot", "refresh", "btn-secondary");
        tree.setOnAction(e -> showProcessTree());
        processes.disableProperty().bind(busy);
        tree.disableProperty().bind(busy);
        VBox processCard = new VBox(12,
                cardTitleRow("cpu", "badge-blue", "Process monitoring",
                        "Reports process starts and ancestry, and flags ransomware-like file activity."),
                new HBox(10, processes, tree));
        processCard.getStyleClass().add("card");

        Label note = new Label("Monitoring only runs while you start it from here and while this "
                + "window is open. It is best-effort and may miss events; it is not a "
                + "replacement for real-time protection.");
        note.getStyleClass().add("card-sub");
        note.setWrapText(true);
        return new VBox(18, grid(2, 16, folderCard, processCard), note);
    }

    private Node buildActivityPage() {
        ToggleGroup view = new ToggleGroup();
        ToggleButton findings = segButton("Findings", view);
        ToggleButton raw = segButton("Console", view);
        findings.setSelected(true);
        HBox seg = new HBox(2, findings, raw);
        seg.getStyleClass().add("seg");
        seg.setMaxWidth(Region.USE_PREF_SIZE);

        ToggleGroup filters = new ToggleGroup();
        HBox chips = new HBox(8);
        addFilter(chips, filters, "All", e -> true).setSelected(true);
        addFilter(chips, filters, "Detections", e -> e.kind() == Kind.DETECTION || e.kind() == Kind.ALERT);
        addFilter(chips, filters, "Suspicious", e -> e.kind() == Kind.SUSPICIOUS);
        addFilter(chips, filters, "Processes", e -> e.kind() == Kind.PROCESS);
        addFilter(chips, filters, "Errors", e -> e.kind() == Kind.ERROR);
        Button clear = button("Clear", "undo", "btn-ghost");
        clear.setOnAction(e -> {
            feed.clear();
            console.clear();
        });
        HBox toolbar = new HBox(14, seg, chips, spacerH(), clear);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        ListView<FeedEvent> list = eventList(filteredFeed, "No events recorded yet.");
        list.setPrefHeight(560);
        VBox listCard = new VBox(list);
        listCard.getStyleClass().add("card");

        console.setPrefHeight(560);
        VBox consoleBox = new VBox(console);
        consoleBox.getStyleClass().add("console-wrap");
        VBox.setVgrow(console, Priority.ALWAYS);

        StackPane switcher = new StackPane(listCard, consoleBox);
        consoleBox.setVisible(false);
        findings.selectedProperty().addListener((o, a, b) -> {
            listCard.setVisible(b);
            consoleBox.setVisible(!b);
            chips.setDisable(!b);
        });
        view.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                a.setSelected(true);
            }
        });
        return new VBox(16, toolbar, switcher);
    }

    private Node buildQuarantinePage() {
        quarantineEnabled.getStyleClass().add("check-box");
        Button browse = button("Browse", "folder", "btn-secondary");
        browse.setOnAction(e -> chooseDirectory(quarantinePath));
        HBox pathRow = new HBox(10, quarantinePath, browse);
        HBox.setHgrow(quarantinePath, Priority.ALWAYS);
        VBox settings = new VBox(12,
                cardHeader("Quarantine policy", "Opt-in for signature and EICAR test detections"),
                quarantineEnabled, pathRow);
        settings.getStyleClass().add("card");

        quarantineEntries = new ListView<>(quarantinedItems);
        quarantineEntries.setPlaceholder(new Label("No quarantined files. Press Refresh to load history."));
        quarantineEntries.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(QuarantineItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label threat = new Label(item.threat());
                threat.getStyleClass().add("event-message");
                threat.setStyle("-fx-font-weight: bold;");
                Label path = new Label(item.originalPath());
                path.getStyleClass().add("event-sub");
                path.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
                Label hash = new Label(item.timestamp() + "  ·  SHA-256 " + item.sha256());
                hash.getStyleClass().add("event-sub");
                VBox box = new VBox(2, threat, path, hash);
                box.setPadding(new Insets(9, 10, 9, 10));
                setGraphic(box);
            }
        });
        quarantineEntries.setPrefHeight(360);
        Button refresh = button("Refresh", "refresh", "btn-secondary");
        refresh.setOnAction(e -> refreshQuarantine());
        Button restore = button("Restore selected", "undo", "btn-primary");
        restore.setOnAction(e -> restoreSelectedQuarantineItem());
        refresh.disableProperty().bind(busy);
        restore.disableProperty().bind(busy);
        VBox history = new VBox(12,
                new HBox(10, cardHeader("History", "Restore verifies the stored SHA-256"), spacerH(), refresh, restore),
                quarantineEntries);
        history.getStyleClass().add("card");
        return new VBox(18, settings, history);
    }

    private Node buildSettingsPage() {
        VBox paths = new VBox(16,
                cardHeader("Locations", "Paths used when launching the engine"),
                pathRow("Engine executable", enginePath, false, () -> chooseFile(enginePath, "*.exe", "Choose engine")),
                pathRow("Signature database", signaturesPath, false, () -> chooseFile(signaturesPath, "*.txt", "Choose signatures")),
                pathRow("Engine configuration", configPath, true, () -> chooseFile(configPath, "*.ini", "Choose configuration")));
        paths.getStyleClass().add("card");

        VBox about = new VBox(8,
                cardHeader("What Sentinel does", "Honest scope of this project"),
                bodyLabel("• On-demand scanning against SHA-256 signatures plus heuristic indicators."),
                bodyLabel("• User-started folder and process monitoring (best-effort)."),
                bodyLabel("• Optional quarantine with hash-verified restore."),
                bodyLabel("• Not an always-on, kernel-level or production antivirus. A clean result is not a safety verdict."));
        about.getStyleClass().add("card");
        return new VBox(18, paths, about);
    }

    // ---------------------------------------------------------------- widgets

    private VBox statCard(String caption, IntegerProperty value, String accent, String note) {
        Label c = new Label(caption);
        c.getStyleClass().add("stat-caption");
        Counter counter = accent == null ? new Counter(value) : new Counter(value, accent);
        Label n = new Label(note);
        n.getStyleClass().add("stat-note");
        VBox card = new VBox(4, c, counter, n);
        card.getStyleClass().add("stat-card");
        return card;
    }

    private VBox riskCard() {
        Label c = new Label("Highest risk /100");
        c.getStyleClass().add("stat-caption");
        Label n = new Label();
        n.getStyleClass().add("stat-note");
        n.textProperty().bind(riskText);
        n.setWrapText(true);
        n.setMinHeight(Region.USE_PREF_SIZE);
        VBox card = new VBox(4, c, new Counter(highestRisk), n);
        card.getStyleClass().add("stat-card");
        return card;
    }

    private VBox scanCard(String iconName, String badge, String title, String text,
            String buttonText, Runnable action) {
        Button run = button(buttonText, "play", "btn-secondary");
        run.setOnAction(e -> action.run());
        run.disableProperty().bind(busy);
        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        VBox card = new VBox(12, cardTitleRow(iconName, badge, title, text), grow, run);
        card.getStyleClass().add("card");
        return card;
    }

    private HBox cardTitleRow(String iconName, String badge, String title, String text) {
        StackPane b = badgeIcon(iconName, badge, 40, 20);
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label d = new Label(text);
        d.getStyleClass().add("card-sub");
        d.setWrapText(true);
        VBox box = new VBox(3, t, d);
        HBox row = new HBox(14, b, box);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(box, Priority.ALWAYS);
        return row;
    }

    private VBox cardHeader(String title, String sub) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label s = new Label(sub);
        s.getStyleClass().add("card-sub");
        return new VBox(2, t, s);
    }

    private Node tile(String iconName, String badge, String title, String desc, Runnable action) {
        Label t = new Label(title);
        t.getStyleClass().add("tile-title");
        Label d = new Label(desc);
        d.getStyleClass().add("tile-desc");
        d.setWrapText(true);
        VBox text = new VBox(2, t, d);
        HBox tile = new HBox(14, badgeIcon(iconName, badge, 40, 20), text);
        tile.setAlignment(Pos.CENTER_LEFT);
        tile.getStyleClass().add("tile");
        tile.setFocusTraversable(true);
        tile.disableProperty().bind(busy);
        tile.setOnMouseClicked(e -> action.run());
        tile.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                action.run();
            }
        });
        return tile;
    }

    private HBox pathRow(String label, TextField field, boolean optional, Runnable browse) {
        Label name = new Label(label);
        name.setMinWidth(150);
        Label validity = new Label();
        validity.getStyleClass().add("validity");
        validity.setMinWidth(78);
        validity.setAlignment(Pos.CENTER_RIGHT);
        Runnable update = () -> {
            String text = field.getText().trim();
            validity.getStyleClass().removeAll("ok", "bad", "optional");
            if (text.isEmpty()) {
                validity.setText(optional ? "Optional" : "Missing");
                validity.getStyleClass().add(optional ? "optional" : "bad");
            } else if (Files.exists(Path.of(text.replaceAll("[\"<>|]", "_")))) {
                validity.setText("Found");
                validity.getStyleClass().add("ok");
            } else {
                validity.setText("Not found");
                validity.getStyleClass().add("bad");
            }
        };
        field.textProperty().addListener((o, a, b) -> {
            try {
                update.run();
            } catch (RuntimeException ignored) {
                validity.setText("Invalid");
            }
        });
        update.run();
        Button b = button("Browse", null, "btn-secondary");
        b.setOnAction(e -> browse.run());
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox row = new HBox(12, name, field, validity, b);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label bodyLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("card-body");
        l.setWrapText(true);
        return l;
    }

    private ToggleButton segButton(String text, ToggleGroup group) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("seg-button");
        b.setToggleGroup(group);
        return b;
    }

    private ToggleButton addFilter(HBox into, ToggleGroup group, String text, Predicate<FeedEvent> predicate) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("filter-chip");
        b.setToggleGroup(group);
        b.setOnAction(e -> {
            if (!b.isSelected()) {
                b.setSelected(true);
            }
            filteredFeed.setPredicate(predicate);
        });
        into.getChildren().add(b);
        return b;
    }

    private ListView<FeedEvent> eventList(ObservableList<FeedEvent> items, String placeholder) {
        ListView<FeedEvent> list = new ListView<>(items);
        list.setFixedCellSize(44);
        Label empty = new Label(placeholder);
        empty.getStyleClass().add("faint");
        list.setPlaceholder(empty);
        list.setFocusTraversable(false);
        list.setCellFactory(v -> new EventCell());
        return list;
    }

    private static final class EventCell extends ListCell<FeedEvent> {
        EventCell() {
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(FeedEvent item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            Label chip = new Label(item.kind().label);
            chip.getStyleClass().addAll("chip", item.kind().css);
            chip.setMinWidth(88);
            chip.setAlignment(Pos.CENTER);
            Label message = new Label(item.message());
            message.getStyleClass().add("event-message");
            message.setMaxWidth(Double.MAX_VALUE);
            message.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
            HBox.setHgrow(message, Priority.ALWAYS);
            Label time = new Label(item.time());
            time.getStyleClass().add("event-time");
            HBox row = new HBox(12, chip, message, time);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(0, 10, 0, 6));
            row.setMinHeight(44);
            setGraphic(row);
            setTooltip(new Tooltip(item.message()));
        }
    }

    private static final class Counter extends Label {
        private final DoubleProperty shown = new SimpleDoubleProperty();
        private Timeline timeline;

        Counter(IntegerProperty source, String... extraStyles) {
            getStyleClass().add("stat-value");
            java.util.List<String> accents = java.util.List.of(extraStyles);
            Runnable recolor = () -> {
                getStyleClass().removeAll(accents);
                if (source.get() > 0) {
                    getStyleClass().addAll(accents);
                }
            };
            recolor.run();
            source.addListener((o, a, b) -> recolor.run());
            setText(source.get() < 0 ? "—" : Integer.toString(source.get()));
            shown.set(Math.max(0, source.get()));
            shown.addListener((o, a, b) -> setText(Long.toString(Math.round(b.doubleValue()))));
            source.addListener((o, a, b) -> animate(b.intValue()));
        }

        private void animate(int target) {
            if (timeline != null) {
                timeline.stop();
            }
            if (target < 0) {
                shown.set(0);
                setText("—");
                return;
            }
            timeline = new Timeline(new KeyFrame(Duration.millis(520),
                    new KeyValue(shown, target, Interpolator.EASE_OUT)));
            timeline.setOnFinished(e -> setText(Integer.toString(target)));
            timeline.play();
        }
    }

    private Button button(String text, String iconName, String variant) {
        Button b = new Button(text);
        b.getStyleClass().addAll("btn", variant);
        if (iconName != null) {
            b.setGraphic(icon(iconName, 16));
        }
        return b;
    }

    private HBox pill(Label first, Label second) {
        first.getStyleClass().add("faint");
        HBox box = new HBox(8, first, second);
        box.getStyleClass().add("pill");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private StackPane badgeIcon(String iconName, String badge, double box, double glyph) {
        StackPane p = new StackPane(svg(iconName, glyph));
        p.getStyleClass().add("badge");
        if (!"badge".equals(badge)) {
            p.getStyleClass().add(badge);
        }
        p.setMinSize(box, box);
        p.setPrefSize(box, box);
        p.setMaxSize(box, box);
        return p;
    }

    private static SVGPath svg(String name, double size) {
        SVGPath path = new SVGPath();
        path.setContent(ICONS.get(name));
        path.getStyleClass().add("icon");
        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);
        path.setStrokeWidth(1.8 / scale);
        return path;
    }

    private static Node icon(String name, double size) {
        StackPane holder = new StackPane(svg(name, size));
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);
        return holder;
    }

    private static GridPane grid(int columns, double gap, Node... nodes) {
        GridPane g = new GridPane();
        g.setHgap(gap);
        g.setVgap(gap);
        for (int i = 0; i < columns; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / columns);
            c.setHgrow(Priority.ALWAYS);
            g.getColumnConstraints().add(c);
        }
        for (int i = 0; i < nodes.length; i++) {
            Node n = nodes[i];
            if (n instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
                r.setMaxHeight(Double.MAX_VALUE);
            }
            GridPane.setFillWidth(n, true);
            GridPane.setFillHeight(n, true);
            g.add(n, i % columns, i / columns);
        }
        return g;
    }

    private static Region spacer(double height) {
        Region r = new Region();
        r.setMinHeight(height);
        return r;
    }

    private static Region spacerH() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // ------------------------------------------------------------ hero & feed

    private void refreshHero() {
        String m = mode.get();
        ShieldGauge.State state = ShieldGauge.State.READY;
        String chip = "READY";
        switch (m) {
            case "Scanning" -> {
                state = ShieldGauge.State.SCANNING;
                chip = "SCANNING";
                headline.set("Scan in progress");
                subline.set("Checking the selected location against local signatures and heuristics.");
            }
            case "Quick scan" -> {
                state = ShieldGauge.State.SCANNING;
                chip = "SCANNING";
                headline.set("Quick scan in progress");
                subline.set("Checking Downloads, temporary and Startup folders for the current user.");
            }
            case "Monitoring", "Monitoring processes" -> {
                state = ShieldGauge.State.SCANNING;
                chip = "MONITORING";
                headline.set("Monitoring active");
                subline.set("User-started monitoring is running. It is best-effort and may miss events.");
            }
            case "Process tree snapshot", "Loading quarantine history", "Restoring quarantine", "Stopping" -> {
                state = ShieldGauge.State.SCANNING;
                chip = "WORKING";
                headline.set(m);
                subline.set("The engine is running a one-off operation.");
            }
            case "Complete" -> {
                if (detections.get() > 0) {
                    state = ShieldGauge.State.ALERT;
                    chip = "ATTENTION";
                    headline.set("Threats detected");
                    subline.set(detections.get() + " detection(s) found. Review them in Activity.");
                } else {
                    headline.set("Scan complete");
                    subline.set("No configured detections in the scanned files. This is not a safety verdict.");
                    chip = "COMPLETE";
                }
            }
            case "Stopped" -> {
                chip = "STOPPED";
                headline.set("Operation stopped");
                subline.set("No background protection is active.");
            }
            case "Error" -> {
                state = ShieldGauge.State.ERROR;
                chip = "ERROR";
                headline.set("Engine needs attention");
                subline.set("Check the engine output and Settings before running another scan.");
            }
            default -> {
                headline.set("Ready to scan");
                subline.set("On-demand scanning is available. Real-time protection is not active.");
            }
        }
        gauge.setState(state);
        chipText.set(chip);
        heroChip.getStyleClass().removeAll("chip-ok", "chip-info", "chip-warn", "chip-danger");
        heroChip.getStyleClass().add(switch (state) {
            case SCANNING -> "chip-info";
            case ALERT, ERROR -> "chip-danger";
            default -> "chip-ok";
        });
    }

    private void addEvent(Kind kind, String message) {
        feed.add(0, new FeedEvent(LocalDateTime.now().format(TIME), kind, message));
        if (feed.size() > 200) {
            feed.remove(feed.size() - 1);
        }
    }

    private static String clean(String line) {
        return LEADING_TAG.matcher(line).replaceFirst("").replace(" | ", "  —  ");
    }

    private void toast(String text, String iconStyle) {
        SVGPath mark = svg("shield", 16);
        mark.getStyleClass().add(iconStyle);
        Label label = new Label(text);
        HBox box = new HBox(10, mark, label);
        box.getStyleClass().add("toast");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setOpacity(0);
        toastLayer.getChildren().add(box);
        FadeTransition in = new FadeTransition(Duration.millis(220), box);
        in.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), box);
        slide.setFromY(14);
        slide.setToY(0);
        PauseTransition hold = new PauseTransition(Duration.seconds(3.6));
        FadeTransition out = new FadeTransition(Duration.millis(300), box);
        out.setToValue(0);
        SequentialTransition all = new SequentialTransition(new ParallelTransition(in, slide), hold, out);
        all.setOnFinished(e -> toastLayer.getChildren().remove(box));
        all.play();
    }

    private void tickElapsed() {
        long seconds = (System.nanoTime() - startedAtNanos) / 1_000_000_000L;
        elapsed.set(String.format("%02d:%02d", seconds / 60, seconds % 60));
    }

    // ----------------------------------------------------------- engine logic

    private void resetStats() {
        filesScanned.set(-1);
        detections.set(0);
        suspicious.set(0);
        highestRisk.set(-1);
        highestRiskScoreObserved = 0;
        riskText.set(NO_INDICATORS);
    }

    private void quickScan() {
        List<String> arguments = baseCommand("quick-scan", null);
        if (arguments == null || !appendQuarantineOption(arguments)) {
            return;
        }
        selectedTarget.set("Downloads + Temp + Startup (current user)");
        resetStats();
        startEngine(arguments, "Quick scan");
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
        Alert confirmation = styled(new Alert(Alert.AlertType.CONFIRMATION));
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

    private Alert styled(Alert alert) {
        alert.initOwner(stage);
        alert.getDialogPane().getStylesheets().add(css);
        alert.setGraphic(null);
        return alert;
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
            selectedTarget.set(selected.getAbsolutePath());
        }
    }

    private void startWatching() {
        String target = selectedTarget.get();
        if (NO_FOLDER.equals(target) || !Files.isDirectory(Path.of(target))) {
            addEvent(Kind.ERROR, "Choose an existing folder before starting monitoring.");
            toast("Choose a folder to monitor first", "bad");
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
        selectedTarget.set(normalized.toString());
        resetStats();
        startEngine(arguments, command.equals("scan") ? "Scanning" : "Monitoring");
        if (command.equals("scan")) {
            showPage("Dashboard");
        } else {
            showPage("Protection");
        }
    }

    private boolean appendQuarantineOption(List<String> arguments) {
        if (quarantineEnabled.isSelected()) {
            String destination = quarantinePath.getText().trim();
            if (destination.isEmpty() && configPath.getText().isBlank()) {
                addEvent(Kind.ERROR, "Set a quarantine folder in Quarantine or turn quarantine off.");
                showPage("Quarantine");
                return false;
            }
            arguments.add("--quarantine");
            if (!destination.isEmpty()) {
                try {
                    arguments.add(Path.of(destination).toAbsolutePath().normalize().toString());
                } catch (RuntimeException error) {
                    addEvent(Kind.ERROR, "Invalid quarantine path: " + error.getMessage());
                    showPage("Quarantine");
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
            addEvent(Kind.ERROR, "Select a quarantine entry before restoring.");
            toast("Select a quarantine entry first", "bad");
            return;
        }
        Alert confirmation = styled(new Alert(Alert.AlertType.CONFIRMATION));
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
                addEvent(Kind.ERROR, "Invalid quarantine path: " + error.getMessage());
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
            addEvent(Kind.ERROR, missingMessage);
            toast(missingMessage, "bad");
            return null;
        }
        try {
            Path path = Path.of(trimmed).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                addEvent(Kind.ERROR, "Path does not exist: " + path);
                toast("Path does not exist: " + path.getFileName(), "bad");
                return null;
            }
            return path;
        } catch (RuntimeException error) {
            addEvent(Kind.ERROR, "Invalid path: " + error.getMessage());
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

    private void startEngine(List<String> arguments, String operation) {
        stopRequestedByUser = false;
        console.clear();
        try {
            engine.start(
                    arguments,
                    this::receiveOutput,
                    (exitCode, failure) -> Platform.runLater(() -> {
                        busy.set(false);
                        elapsedTimer.stop();
                        engineStatus.set("Idle");
                        if (failure != null) {
                            mode.set("Error");
                            addEvent(Kind.ERROR, "Engine communication failed: " + failure.getMessage());
                            toast("Engine communication failed", "bad");
                        } else if (stopRequestedByUser) {
                            mode.set("Stopped");
                            addEvent(Kind.INFO, "Engine operation stopped.");
                        } else if (exitCode == 0) {
                            mode.set("Complete");
                            addEvent(Kind.INFO, "Engine operation completed.");
                            if ("Restoring quarantine".equals(operation)) {
                                refreshQuarantine();
                            } else if (!"Loading quarantine history".equals(operation)) {
                                if (detections.get() > 0) {
                                    toast(operation + " finished — " + detections.get()
                                            + " detection(s)", "bad");
                                } else {
                                    toast(operation + " finished", "ok");
                                }
                            }
                        } else {
                            mode.set("Error");
                            addEvent(Kind.ERROR, "Engine exited with code " + exitCode + ".");
                            toast("Engine exited with code " + exitCode, "bad");
                        }
                    }));
            busy.set(true);
            mode.set(operation);
            engineStatus.set("Running");
            startedAtNanos = System.nanoTime();
            elapsed.set("00:00");
            elapsedTimer.playFromStart();
            appendConsole("$ " + String.join(" ", arguments));
            addEvent(Kind.INFO, operation + " started.");
        } catch (Exception error) {
            mode.set("Error");
            engineStatus.set("Unavailable");
            addEvent(Kind.ERROR, "Could not start engine: " + error.getMessage());
            toast("Could not start engine", "bad");
        }
    }

    private final java.util.concurrent.ConcurrentLinkedQueue<String> pendingLines =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicBoolean drainScheduled =
            new java.util.concurrent.atomic.AtomicBoolean();

    private void receiveOutput(String line) {
        pendingLines.add(line);
        if (drainScheduled.compareAndSet(false, true)) {
            Platform.runLater(this::drainOutput);
        }
    }

    private void drainOutput() {
        StringBuilder batch = new StringBuilder();
        java.util.List<String> lines = new java.util.ArrayList<>();
        String next;
        while (lines.size() < 300 && (next = pendingLines.poll()) != null) {
            lines.add(next);
            batch.append(next).append(System.lineSeparator());
        }
        appendConsoleBatch(batch.toString());
        for (String line : lines) {
            processLine(line);
        }
        drainScheduled.set(false);
        if (!pendingLines.isEmpty() && drainScheduled.compareAndSet(false, true)) {
            Platform.runLater(this::drainOutput);
        }
    }

    private void processLine(String line) {
        {
            Matcher riskMatcher = FILE_RISK.matcher(line);
            if (riskMatcher.find()) {
                int score = Integer.parseInt(riskMatcher.group(1));
                if (score > highestRiskScoreObserved) {
                    highestRiskScoreObserved = score;
                    highestRisk.set(score);
                    riskText.set(riskMatcher.group(2).trim() + " — " + riskMatcher.group(3).trim());
                }
            }
            if (line.startsWith("QUARANTINE_ENTRY\t")) {
                try {
                    quarantinedItems.add(parseQuarantineEntry(line));
                } catch (IllegalArgumentException error) {
                    addEvent(Kind.ERROR, "Invalid quarantine history entry: " + error.getMessage());
                }
            } else if (line.equals("QUARANTINE_EMPTY")) {
                quarantinedItems.clear();
            } else if (line.contains("[DETECTED]")) {
                detections.set(detections.get() + 1);
                addEvent(Kind.DETECTION, clean(line));
            } else if (line.contains("[SUSPICIOUS]")) {
                suspicious.set(suspicious.get() + 1);
                addEvent(Kind.SUSPICIOUS, clean(line));
            } else if (line.contains("[PROCESS START]")) {
                addEvent(Kind.PROCESS, clean(line));
            } else if (line.startsWith("[PROCESS TREE]")) {
                addEvent(Kind.PROCESS, clean(line));
            } else if (line.startsWith("[RANSOMWARE-LIKE ACTIVITY]")) {
                addEvent(Kind.ALERT, clean(line));
            } else if (line.startsWith("[ERROR]") || line.startsWith("[FATAL]")) {
                addEvent(Kind.ERROR, clean(line));
            } else {
                Matcher quickMatcher = QUICK_SCAN_SUMMARY.matcher(line);
                if (quickMatcher.find()) {
                    filesScanned.set(Integer.parseInt(quickMatcher.group(2)));
                    detections.set(Integer.parseInt(quickMatcher.group(3)));
                    suspicious.set(Integer.parseInt(quickMatcher.group(4)));
                    int score = Integer.parseInt(quickMatcher.group(7));
                    if (score == 0) {
                        riskText.set(NO_INDICATORS);
                        highestRisk.set(0);
                    } else if (score >= highestRiskScoreObserved) {
                        highestRiskScoreObserved = score;
                        highestRisk.set(score);
                        riskText.set(quickMatcher.group(8));
                    }
                    addEvent(Kind.SCAN,
                            quickMatcher.group(1) + " location(s), "
                                    + quickMatcher.group(2) + " file(s), "
                                    + quickMatcher.group(5) + " skipped, "
                                    + quickMatcher.group(6) + " error(s)");
                } else {
                    Matcher matcher = SCAN_SUMMARY.matcher(line);
                    if (matcher.find()) {
                        filesScanned.set(Integer.parseInt(matcher.group(1)));
                        detections.set(Integer.parseInt(matcher.group(2)));
                        suspicious.set(Integer.parseInt(matcher.group(3)));
                        if (highestRisk.get() < 0) {
                            highestRisk.set(0);
                        }
                        addEvent(Kind.SCAN, line);
                    }
                }
            }
        }
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

    private void appendConsole(String line) {
        appendConsoleBatch(line + System.lineSeparator());
    }

    private void appendConsoleBatch(String text) {
        console.appendText(text);
        if (console.getParagraphs().size() > 1500) {
            String[] all = console.getText().split("\n", -1);
            int keep = 1000;
            String trimmed = String.join("\n", java.util.Arrays.copyOfRange(all, all.length - keep, all.length));
            console.setText(trimmed);
            console.positionCaret(trimmed.length());
        }
    }

    private void stopEngine() {
        if (engine.isRunning()) {
            stopRequestedByUser = true;
            engine.stop();
            mode.set("Stopping");
            addEvent(Kind.INFO, "Stop requested for active engine process.");
        }
    }

    private static List<Path> candidates(String name, Path... relative) {
        List<Path> list = new java.util.ArrayList<>();
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null) {
            Path dir = Path.of(appPath).toAbsolutePath().getParent();
            list.add(dir.resolve("content").resolve(name));
            list.add(dir.resolve(name));
            list.add(dir.resolve("app").resolve(name));
        }
        list.addAll(List.of(relative));
        return list;
    }

    private static String defaultEnginePath() {
        for (Path candidate : candidates("sentinel-av.exe",
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
        for (Path candidate : candidates("signatures.example.txt",
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
        for (Path candidate : candidates("config.example.ini",
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

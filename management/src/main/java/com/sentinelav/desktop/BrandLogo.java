package com.sentinelav.desktop;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Sentinel AV brand mark: a shield carrying a stylised "S". Drawn in code, no image assets. */
final class BrandLogo {
    private static final Color TOP = Color.web("#5eead4");
    private static final Color BOTTOM = Color.web("#0d9488");

    private BrandLogo() {
    }

    /** Builds the shield outline in a coordinate system centred on (0,0) with half-size s. */
    static void shieldPath(GraphicsContext g, double s) {
        g.beginPath();
        g.moveTo(0, -s);
        g.bezierCurveTo(s * 0.35, -s * 0.80, s * 0.70, -s * 0.78, s * 0.90, -s * 0.70);
        g.lineTo(s * 0.90, s * 0.05);
        g.bezierCurveTo(s * 0.90, s * 0.55, s * 0.45, s * 0.85, 0, s * 1.05);
        g.bezierCurveTo(-s * 0.45, s * 0.85, -s * 0.90, s * 0.55, -s * 0.90, s * 0.05);
        g.lineTo(-s * 0.90, -s * 0.70);
        g.bezierCurveTo(-s * 0.70, -s * 0.78, -s * 0.35, -s * 0.80, 0, -s);
        g.closePath();
    }

    /** Strokes the "S" monogram with the current stroke paint and line width. */
    static void strokeS(GraphicsContext g, double s) {
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.beginPath();
        g.moveTo(s * 0.34, -s * 0.36);
        g.bezierCurveTo(s * 0.18, -s * 0.56, -s * 0.42, -s * 0.52, -s * 0.42, -s * 0.22);
        g.bezierCurveTo(-s * 0.42, s * 0.04, s * 0.42, -s * 0.02, s * 0.42, s * 0.28);
        g.bezierCurveTo(s * 0.42, s * 0.56, -s * 0.18, s * 0.58, -s * 0.34, s * 0.38);
        g.stroke();
    }

    /** Draws the full-colour logo into a square canvas. */
    static Canvas canvas(double size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        double s = size * 0.40;
        g.translate(size / 2, size * 0.49);
        shieldPath(g, s);
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, TOP), new Stop(1, BOTTOM)));
        g.fill();
        g.setStroke(Color.web("#06201f"));
        g.setLineWidth(Math.max(2, size * 0.075));
        strokeS(g, s);
        return canvas;
    }

    /** Rasterises the logo, used for the window and taskbar icon. */
    static Image icon(int size) {
        Canvas canvas = canvas(size);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    static Canvas glowing(double size) {
        Canvas canvas = canvas(size);
        canvas.setEffect(new DropShadow(size * 0.35, Color.web("#2dd4bf", 0.45)));
        return canvas;
    }
}

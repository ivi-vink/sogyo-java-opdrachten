package nl.sogyo.javaopdrachten.raytracer.raytracer.scene;

import nl.sogyo.javaopdrachten.raytracer.raytracer.anglecalculator.AngleCalculator;
import nl.sogyo.javaopdrachten.raytracer.raytracer.exceptions.NoIntersectionPossible;
import nl.sogyo.javaopdrachten.raytracer.raytracer.shapes.Line;
import nl.sogyo.javaopdrachten.raytracer.raytracer.shapes.Shape;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class Scene {
    private Vector viewpoint;
    private Viewport viewport;
    private ArrayList<Lightsource> myLightsources = new ArrayList<>();
    private ArrayList<nl.sogyo.javaopdrachten.raytracer.raytracer.shapes.Shape> myShapes = new ArrayList<>();

    private AngleCalculator angleCalculator = new AngleCalculator();

    private float maxBrightness;
    private final BufferedImage image;
    private static final double EPSILON = 1e-5;

    public Scene(Vector viewpoint, Viewport viewport, Lightsource[] lightsources, nl.sogyo.javaopdrachten.raytracer.raytracer.shapes.Shape[] shapes) {
        this.viewpoint = viewpoint;
        this.viewport = viewport;
        for (Lightsource lightsource: lightsources)
            myLightsources.add(lightsource);
        for (nl.sogyo.javaopdrachten.raytracer.raytracer.shapes.Shape shape: shapes)
            myShapes.add(shape);

        maxBrightness = 0;
        for (Lightsource lightsource: lightsources) maxBrightness = maxBrightness + lightsource.getBrightness();

        image =  new BufferedImage(viewport.getWidth(), viewport.getHeight(), BufferedImage.TYPE_BYTE_INDEXED);

    }

    public String toString() {
        String shapes = "";
        for (nl.sogyo.javaopdrachten.raytracer.raytracer.shapes.Shape shape: this.myShapes)
            shapes = shapes + shape.toString() + "\n";

        String lightsources = "";
        for (Lightsource lightsource: this.myLightsources)
            lightsources = lightsources + lightsource.toString() + "\n";
        return "viewpoint: " + viewpoint +
                "\nviewport: " + viewport + "\n" +
                shapes +
                lightsources;
    }

    public void draw() {
        for (int i = 0; i < viewport.getWidth(); i++) {
            for (int j = 0; j < viewport.getHeight(); j++) {
                Vector pixel = viewport.getVector(new Coordinate(i,j));

                float brightness = calculatePixelBrightness(pixel);
                brightnessValueToImage(i, j, brightness);
            }
        }
        writeImage();
    }

    private float calculatePixelBrightness(Vector pixel) {
        // make a line
        Line line = new Line(viewpoint, pixel);
        float distanceFromViewpointToPixel = pixel.subtract(viewpoint).getModulus();

        // Get nearest intersection along line, after viewport
        Intersection intersection = nearestIntersection(line, distanceFromViewpointToPixel);

        if (!(intersection == null)) {
            return brightnessOfReflection(intersection, line);
        }
        return 0f;
    }

    private float brightnessOfReflection(Intersection intersection, Line lineFromViewpoint) {
        float angleOfIntersectionWithShape = intersection.getAngleOfIntersection();
        Vector intersectionPoint = intersection.getPoint();
        float brightness = 0f;

        for (Lightsource lightsource: myLightsources) {
            Line lineToLight = new Line(intersectionPoint, lightsource.getPosition());

            if (angleOfIntersectionWithShape > Math.PI / 2 && angleOfIntersectionWithShape < Math.PI * 3 / 2) {
                brightness = brightness + reflectionOutsideShape(intersection, lightsource, lineToLight);
            } else {
                brightness = brightness + reflectionFromInsideShape(intersection, lightsource, lineToLight);
            }
        }
        return brightness;
    }

    private float reflectionFromInsideShape(Intersection intersection, Lightsource lightsource, Line lineToLight) {
        Vector intersectionNormal = intersection.getNormal();
        Vector lineToLightDirection = lineToLight.parametric().direction();

        if (lineToLightDirection.dotProduct(intersectionNormal) > 0) return 0;

        try {
            ArrayList<Vector> points = new ArrayList<>(Arrays.asList(intersection.getShape().intersect(lineToLight)));
            points = removeIntersectionPoint(intersection, points);
            for (Vector point: points) {
                boolean lightSourceOutside = point.subtract(intersection.getPoint()).getModulus() < lightsource.getPosition().subtract(intersection.getPoint()).getModulus();
                boolean pointIsInRightDirection = point.subtract(intersection.getPoint()).dotProduct(lineToLightDirection) > 0;
                boolean outside = lightSourceOutside && pointIsInRightDirection;

                if (outside) return 0f;
            }
        } catch (NoIntersectionPossible noIntersectionPossible) {

        }

        ArrayList<Shape> copy = new ArrayList<>(myShapes);
        copy.remove(intersection.getShape());
        for (Shape shape: copy) {
            try {
                Vector[] points = shape.intersect(lineToLight);
                for (Vector point: points) {
                    boolean pointIsToofar = point.subtract(intersection.getPoint()).getModulus() > lightsource.getPosition().subtract(intersection.getPoint()).getModulus();
                    boolean pointIsInRightDirection = point.subtract(intersection.getPoint()).dotProduct(lineToLightDirection) > 0;
                    boolean doesntblock = pointIsToofar && pointIsInRightDirection;

                    if (doesntblock) continue;
                    else if (!pointIsInRightDirection) continue;
                    return 0;
                }

            } catch (NoIntersectionPossible e) {
                continue;
            }
        }


        return lightsource.getBrightness();
    }

    private ArrayList<Vector> removeIntersectionPoint(Intersection intersection, ArrayList<Vector> points) {
        Vector pointOfInterest = intersection.getPoint();
        Vector nearest = null;
        for (Vector point: points) {
            if (nearest == null) {
                nearest = point;
            }
            if (point.subtract(pointOfInterest).getModulus() < nearest.subtract(pointOfInterest).getModulus()) {
                nearest = point;
            }
        }
        points.remove(nearest);
        return points;
    }

    private float reflectionOutsideShape(Intersection intersection, Lightsource lightsource, Line lineToLight) {
        Vector intersectionNormal = intersection.getNormal();
        Vector lineToLightDirection = lineToLight.parametric().direction();

        if (lineToLightDirection.dotProduct(intersectionNormal) < 0) return 0;

        for (Shape shape: myShapes) {
            try {
                if (shape == intersection.getShape()) continue;

                Vector[] points = shape.intersect(lineToLight);
                for (Vector point: points) {
                    boolean pointIsToofar = point.subtract(intersection.getPoint()).getModulus() > lightsource.getPosition().subtract(intersection.getPoint()).getModulus();
                    boolean pointIsInRightDirection = point.subtract(intersection.getPoint()).dotProduct(lineToLightDirection) > 0;
                    boolean doesntblock = pointIsToofar && pointIsInRightDirection;

                    if (doesntblock) continue;
                    else if (!pointIsInRightDirection) continue;
                    return 0;
                }

            } catch (NoIntersectionPossible e) {
                continue;
            }
        }
        return lightsource.getBrightness();
    }

    private Intersection nearestIntersection(Line line, float distance) {
        AngleCalculator angleCalculator = new AngleCalculator();
        Intersection intersection = null;
        Intersection nearestIntersection = null;

        for (Shape shape: myShapes) {
            Vector[] intersectionPoints;
            try {
                intersectionPoints = shape.intersect(line);
            } catch (NoIntersectionPossible e) {
                continue;
            }

            for (Vector point: intersectionPoints) {


                if (line.parametric().direction().dotProduct(point.subtract(viewpoint)) > 0) {
                    intersection = new Intersection(
                            point,
                            shape.calculateAngle(line, point),
                            line,
                            shape
                    );
                    if (nearestIntersection == null) {
                        nearestIntersection = intersection;
                        continue;
                    }

                    if (nearestIntersection.getPoint().subtract(viewpoint).getModulus() > point.subtract(viewpoint).getModulus()) {
                    } else {
                        nearestIntersection = intersection;
                    }
                }
            }
        }

        return intersection;
    }

    private void brightnessValueToImage(int row, int col, float brightness) {
        int brightnessAdjustedPixel;
        if (brightness != 0)
            brightnessAdjustedPixel = (int) ((brightness / maxBrightness) * 255);
        else
            brightnessAdjustedPixel = 0;

        Color color = new Color(brightnessAdjustedPixel, brightnessAdjustedPixel, brightnessAdjustedPixel);
        image.setRGB(row, col, color.getRGB());
        }

    public void writeImage() {
        File output = new File("Grayscale.jpg");
        try {
            ImageIO.write(image, "jpg", output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Viewport getViewport() {
        return viewport;
    }
}

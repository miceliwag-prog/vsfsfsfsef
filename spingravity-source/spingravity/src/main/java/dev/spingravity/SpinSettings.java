package dev.spingravity;

/**
 * The physics of a rotating habitat. No Minecraft types in here on purpose.
 *
 * 1 block = 1 metre. Standing on the inside of a cylinder of radius R spinning at w rad/s,
 * you feel a = w^2 * r, where r is your distance from the spin axis.
 * "Floor" is the height where r == radius (sea level by default); going up moves you
 * towards the axis (less gravity), going down moves you away from it (more gravity).
 */
public final class SpinSettings {
    public static final double STANDARD_G = 9.80665; // m/s^2 in one G

    public boolean enabled = true;
    /** Distance from the floor height to the spin axis, in blocks. */
    public double radius = 1000.0;
    /** Y level of the floor ("sea level" in the overworld is 63). */
    public double floorY = 63.0;
    public double rpm = rpmForFloorG(1000.0, 1.0);

    public double omega() {
        return rpm * 2.0 * Math.PI / 60.0;
    }

    /** Gravity in Gs at the floor (sea level). */
    public double floorG() {
        return omega() * omega() * radius / STANDARD_G;
    }

    /** Gravity in Gs at world height y. Zero at/above the axis. */
    public double gAt(double y) {
        double r = radius - (y - floorY);
        if (r <= 0.0) return 0.0;
        return omega() * omega() * r / STANDARD_G;
    }

    /** Speed of the floor in m/s. */
    public double rimSpeed() {
        return omega() * radius;
    }

    /** Seconds per revolution (infinity if not spinning). */
    public double periodSeconds() {
        return rpm <= 0.0 ? Double.POSITIVE_INFINITY : 60.0 / rpm;
    }

    /** World Y of the spin axis. */
    public double axisY() {
        return floorY + radius;
    }

    public void setFloorG(double g) {
        this.rpm = rpmForFloorG(radius, g);
    }

    public static double rpmForFloorG(double radius, double g) {
        return Math.sqrt(g * STANDARD_G / radius) * 60.0 / (2.0 * Math.PI);
    }
}

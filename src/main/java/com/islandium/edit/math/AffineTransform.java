package com.islandium.edit.math;

import org.jetbrains.annotations.NotNull;

/**
 * An affine transform for 3D transformations.
 * Based on WorldEdit's AffineTransform.
 *
 * Uses a 3x4 matrix to represent the transformation:
 * [m00 m01 m02 m03]   [x]
 * [m10 m11 m12 m13] * [y]
 * [m20 m21 m22 m23]   [z]
 *                     [1]
 */
public class AffineTransform {

    // Coefficients for x coordinate
    private final double m00, m01, m02, m03;
    // Coefficients for y coordinate
    private final double m10, m11, m12, m13;
    // Coefficients for z coordinate
    private final double m20, m21, m22, m23;

    /**
     * Creates identity transform.
     */
    public AffineTransform() {
        this(
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0
        );
    }

    /**
     * Creates a transform with the given coefficients.
     */
    public AffineTransform(
            double m00, double m01, double m02, double m03,
            double m10, double m11, double m12, double m13,
            double m20, double m21, double m22, double m23) {
        this.m00 = m00; this.m01 = m01; this.m02 = m02; this.m03 = m03;
        this.m10 = m10; this.m11 = m11; this.m12 = m12; this.m13 = m13;
        this.m20 = m20; this.m21 = m21; this.m22 = m22; this.m23 = m23;
    }

    /**
     * Returns whether this is the identity transform.
     */
    public boolean isIdentity() {
        return m00 == 1 && m11 == 1 && m22 == 1
            && m01 == 0 && m02 == 0 && m03 == 0
            && m10 == 0 && m12 == 0 && m13 == 0
            && m20 == 0 && m21 == 0 && m23 == 0;
    }

    /**
     * Returns the determinant of this transform.
     */
    private double determinant() {
        return m00 * (m11 * m22 - m12 * m21)
             - m01 * (m10 * m22 - m20 * m12)
             + m02 * (m10 * m21 - m20 * m11);
    }

    /**
     * Returns the inverse of this transform.
     */
    @NotNull
    public AffineTransform inverse() {
        if (isIdentity()) {
            return this;
        }
        double det = determinant();
        if (Math.abs(det) < 1e-10) {
            throw new IllegalStateException("Transform is not invertible");
        }
        return new AffineTransform(
            (m11 * m22 - m21 * m12) / det,
            (m21 * m02 - m01 * m22) / det,
            (m01 * m12 - m11 * m02) / det,
            (m01 * (m22 * m13 - m12 * m23) + m02 * (m11 * m23 - m21 * m13) - m03 * (m11 * m22 - m21 * m12)) / det,
            (m20 * m12 - m10 * m22) / det,
            (m00 * m22 - m20 * m02) / det,
            (m10 * m02 - m00 * m12) / det,
            (m00 * (m12 * m23 - m22 * m13) - m02 * (m10 * m23 - m20 * m13) + m03 * (m10 * m22 - m20 * m12)) / det,
            (m10 * m21 - m20 * m11) / det,
            (m20 * m01 - m00 * m21) / det,
            (m00 * m11 - m10 * m01) / det,
            (m00 * (m21 * m13 - m11 * m23) + m01 * (m10 * m23 - m20 * m13) - m03 * (m10 * m21 - m20 * m11)) / det
        );
    }

    /**
     * Concatenates this transform with another.
     * Returns the composition: this * that
     */
    @NotNull
    public AffineTransform concatenate(@NotNull AffineTransform that) {
        return new AffineTransform(
            m00 * that.m00 + m01 * that.m10 + m02 * that.m20,
            m00 * that.m01 + m01 * that.m11 + m02 * that.m21,
            m00 * that.m02 + m01 * that.m12 + m02 * that.m22,
            m00 * that.m03 + m01 * that.m13 + m02 * that.m23 + m03,
            m10 * that.m00 + m11 * that.m10 + m12 * that.m20,
            m10 * that.m01 + m11 * that.m11 + m12 * that.m21,
            m10 * that.m02 + m11 * that.m12 + m12 * that.m22,
            m10 * that.m03 + m11 * that.m13 + m12 * that.m23 + m13,
            m20 * that.m00 + m21 * that.m10 + m22 * that.m20,
            m20 * that.m01 + m21 * that.m11 + m22 * that.m21,
            m20 * that.m02 + m21 * that.m12 + m22 * that.m22,
            m20 * that.m03 + m21 * that.m13 + m22 * that.m23 + m23
        );
    }

    /**
     * Combines this transform with another.
     */
    @NotNull
    public AffineTransform combine(@NotNull AffineTransform other) {
        return concatenate(other);
    }

    /**
     * Creates a translation transform.
     */
    @NotNull
    public AffineTransform translate(double x, double y, double z) {
        return concatenate(new AffineTransform(
            1, 0, 0, x,
            0, 1, 0, y,
            0, 0, 1, z
        ));
    }

    /**
     * Creates a scale transform.
     */
    @NotNull
    public AffineTransform scale(double sx, double sy, double sz) {
        return concatenate(new AffineTransform(
            sx, 0, 0, 0,
            0, sy, 0, 0,
            0, 0, sz, 0
        ));
    }

    /**
     * Creates a rotation around the Y axis (yaw).
     * Positive angle = clockwise when viewed from above (like WorldEdit).
     *
     * WorldEdit convention: //rotate 90 turns clipboard clockwise (North->East)
     *
     * @param degrees rotation in degrees
     */
    @NotNull
    public AffineTransform rotateY(double degrees) {
        // WorldEdit uses direct angle (no negation)
        // Matrix: [cos 0 sin; 0 1 0; -sin 0 cos]
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        return concatenate(new AffineTransform(
            cos, 0, sin, 0,
            0, 1, 0, 0,
            -sin, 0, cos, 0
        ));
    }

    /**
     * Creates a rotation around the X axis (pitch).
     *
     * @param degrees rotation in degrees
     */
    @NotNull
    public AffineTransform rotateX(double degrees) {
        double rad = Math.toRadians(-degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return concatenate(new AffineTransform(
            1, 0, 0, 0,
            0, cos, -sin, 0,
            0, sin, cos, 0
        ));
    }

    /**
     * Creates a rotation around the Z axis (roll).
     *
     * @param degrees rotation in degrees
     */
    @NotNull
    public AffineTransform rotateZ(double degrees) {
        double rad = Math.toRadians(-degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return concatenate(new AffineTransform(
            cos, -sin, 0, 0,
            sin, cos, 0, 0,
            0, 0, 1, 0
        ));
    }

    /**
     * Applies this transform to a point.
     *
     * @return transformed coordinates as [x, y, z]
     */
    @NotNull
    public double[] apply(double x, double y, double z) {
        return new double[] {
            x * m00 + y * m01 + z * m02 + m03,
            x * m10 + y * m11 + z * m12 + m13,
            x * m20 + y * m21 + z * m22 + m23
        };
    }

    /**
     * Applies this transform to integer coordinates and floors the result.
     *
     * @return transformed coordinates as [x, y, z]
     */
    @NotNull
    public int[] applyFloor(int x, int y, int z) {
        double[] result = apply(x, y, z);
        return new int[] {
            (int) Math.floor(result[0]),
            (int) Math.floor(result[1]),
            (int) Math.floor(result[2])
        };
    }

    /**
     * Returns if this affine transform represents a horizontal flip.
     * Used for block state transformations.
     */
    public boolean isHorizontalFlip() {
        // Use the determinant of the x-z submatrix to check if this is a horizontal flip
        return m00 * m22 - m02 * m20 < 0;
    }

    /**
     * Returns if this transform flips the X axis (east/west).
     *
     * La detection utilise le determinant de la sous-matrice XZ pour savoir
     * s'il y a un flip reel (det < 0). Si det >= 0, c'est une rotation pure
     * (meme si m00 et m22 sont negatifs — double flip = rotation 180).
     *
     * Quand det < 0, on determine le TYPE de flip par la decomposition :
     * - R(theta) x FlipZ donne m22 negatif quand cos(theta) > 0, et m00 negatif quand cos < 0
     * - On utilise la meme logique que getYRotation() pour identifier le flip
     *
     * Convention: si det < 0 et que la decomposition donne flipX, retourne true.
     * FlipX = m00 < 0 et m22 >= 0 apres extraction de la rotation.
     * En pratique: det < 0 et m22 >= 0 (l'axe Z n'est pas flippe, donc c'est X).
     * Cas speciaux (m22 == 0): on utilise m20 pour trancher.
     */
    public boolean isFlipX() {
        double det = m00 * m22 - m02 * m20;
        if (det >= 0) return false; // Pas de flip (rotation pure)
        // det < 0 : il y a un flip. Lequel ?
        // FlipZ: m22 < 0 OU (m22==0 et m20 et m02 de meme signe = forme R*FlipZ)
        // FlipX: tout le reste
        if (Math.abs(m22) > 0.5) {
            // m22 significatif : si m22 < 0 c'est flipZ, sinon flipX
            return m22 > 0;
        } else {
            // m22 ~ 0, rotation 90 ou 270 deg + flip
            // R(90)*FlipZ  = [0, -1;  -1, 0] -> m02=-1, m20=-1 (meme signe)
            // R(90)*FlipX  = [0,  1;   1, 0] -> m02=+1, m20=+1 (meme signe aussi)
            // R(270)*FlipZ = [0,  1;   1, 0] -> m02=+1, m20=+1
            // R(270)*FlipX = [0, -1;  -1, 0] -> m02=-1, m20=-1
            // En fait les deux sont indistinguables car FlipX = R(180)*FlipZ
            // Convention: utiliser m20 > 0 comme indicateur flipX
            return m20 > 0;
        }
    }

    /**
     * Returns if this transform flips the Z axis (north/south).
     * Meme logique que isFlipX() : det < 0 requis, puis on identifie le type.
     */
    public boolean isFlipZ() {
        double det = m00 * m22 - m02 * m20;
        if (det >= 0) return false; // Pas de flip (rotation pure)
        // det < 0 : il y a un flip. C'est flipZ si ce n'est pas flipX.
        return !isFlipX();
    }

    /**
     * Returns if this affine transform represents a vertical flip.
     */
    public boolean isVerticalFlip() {
        return m11 < 0;
    }

    /**
     * Gets the effective rotation around the Y axis in degrees.
     * Used for rotating block states.
     *
     * @return rotation in degrees (0, 90, 180, or 270)
     */
    public int getYRotation() {
        // Extract rotation from the matrix, ignoring flip components.
        // The XZ submatrix can be decomposed as: rotation * flip (if any).
        // For a pure rotation: m00=cos, m02=sin, m20=-sin, m22=cos
        // For rotation + flipZ: m00=cos, m02=sin, m20=sin, m22=-cos
        // For rotation + flipX: m00=-cos, m02=-sin, m20=-sin, m22=cos
        //
        // The determinant of the XZ submatrix is +1 for pure rotation, -1 for rotation+flip.
        // To extract just the rotation angle, we use atan2 on the appropriate elements.

        double det = m00 * m22 - m02 * m20;
        double cos, sin;

        if (det >= 0) {
            // No flip: standard rotation matrix
            cos = m00;
            sin = m02;
        } else {
            // Has flip: extract rotation component
            // If flipZ (m22 < 0): rotation matrix is [m00, m02; -m02, m00] * [1,0;0,-1]
            //   so cos = m00, sin = m02
            // If flipX (m00 < 0): rotation matrix is [-1,0;0,1] * [cos, sin; -sin, cos]
            //   which gives [-cos, -sin; -sin, cos], so cos = -m00, sin = -m02
            //   But we can also just use: cos = m22, sin = m20 for flipX
            if (m22 < 0) {
                // flipZ present
                cos = m00;
                sin = m02;
            } else {
                // flipX present
                cos = m22;
                sin = -m20;
            }
        }

        // Determine rotation based on cos/sin values
        if (cos > 0.5) {
            return 0;
        } else if (sin > 0.5) {
            return 90;
        } else if (cos < -0.5) {
            return 180;
        } else {
            return 270;
        }
    }

    @Override
    public String toString() {
        return String.format("AffineTransform[%.2f %.2f %.2f %.2f, %.2f %.2f %.2f %.2f, %.2f %.2f %.2f %.2f]",
            m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23);
    }

    /**
     * Debug method to test transformations.
     * Run: java com.islandium.edit.math.AffineTransform
     */
    public static void main(String[] args) {
        System.out.println("=== AffineTransform Debug Test ===\n");

        // Test 1: Rotation 90° clockwise
        System.out.println("Test 1: Rotate 90° (should turn North->East)");
        AffineTransform rot90 = new AffineTransform().rotateY(90);
        System.out.println("Matrix: " + rot90);

        // Point North (0, 0, -1) should go to East (1, 0, 0)
        double[] p1 = rot90.apply(0, 0, -1);
        System.out.printf("(0, 0, -1) -> (%.2f, %.2f, %.2f) [expected: (1, 0, 0) or similar]\n\n", p1[0], p1[1], p1[2]);

        // Point East (1, 0, 0) should go to South (0, 0, 1)
        double[] p2 = rot90.apply(1, 0, 0);
        System.out.printf("(1, 0, 0) -> (%.2f, %.2f, %.2f) [expected: (0, 0, 1)]\n\n", p2[0], p2[1], p2[2]);

        // Test 2: Flip X (East<->West mirror)
        System.out.println("Test 2: Flip X (East<->West mirror)");
        AffineTransform flipX = new AffineTransform().scale(-1, 1, 1);
        System.out.println("Matrix: " + flipX);

        // Point (2, 0, 3) should go to (-2, 0, 3)
        double[] p3 = flipX.apply(2, 0, 3);
        System.out.printf("(2, 0, 3) -> (%.2f, %.2f, %.2f) [expected: (-2, 0, 3)]\n\n", p3[0], p3[1], p3[2]);

        // Test 3: Flip Z (North<->South mirror)
        System.out.println("Test 3: Flip Z (North<->South mirror)");
        AffineTransform flipZ = new AffineTransform().scale(1, 1, -1);
        System.out.println("Matrix: " + flipZ);

        // Point (2, 0, 3) should go to (2, 0, -3)
        double[] p4 = flipZ.apply(2, 0, 3);
        System.out.printf("(2, 0, 3) -> (%.2f, %.2f, %.2f) [expected: (2, 0, -3)]\n\n", p4[0], p4[1], p4[2]);

        // Test 4: Combination Rotate 90 + Flip X
        System.out.println("Test 4: Rotate 90° then Flip X");
        AffineTransform combined = new AffineTransform().rotateY(90).scale(-1, 1, 1);
        System.out.println("Matrix: " + combined);

        double[] p5 = combined.apply(2, 0, 3);
        System.out.printf("(2, 0, 3) -> (%.2f, %.2f, %.2f)\n\n", p5[0], p5[1], p5[2]);
    }
}

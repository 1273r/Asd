package com.example.robloxphysics.rbx;

import org.joml.Quaternionf;

/**
 * Roblox CFrame: position + orthonormal rotation matrix (row major).
 * Axes follow Roblox: RightVector = column 0, UpVector = column 1, LookVector = -column 2.
 * Minecraft and Roblox are both right-handed and Y-up, so no axis swapping is needed.
 */
public final class CFrame {
    public static final CFrame IDENTITY = new CFrame(0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1);

    public final double x, y, z;
    public final double r00, r01, r02, r10, r11, r12, r20, r21, r22;

    public CFrame(double x, double y, double z,
                  double r00, double r01, double r02,
                  double r10, double r11, double r12,
                  double r20, double r21, double r22) {
        this.x = x; this.y = y; this.z = z;
        this.r00 = r00; this.r01 = r01; this.r02 = r02;
        this.r10 = r10; this.r11 = r11; this.r12 = r12;
        this.r20 = r20; this.r21 = r21; this.r22 = r22;
    }

    public static CFrame at(V3 p) { return new CFrame(p.x(), p.y(), p.z(), 1, 0, 0, 0, 1, 0, 0, 0, 1); }

    public static CFrame at(double x, double y, double z) { return new CFrame(x, y, z, 1, 0, 0, 0, 1, 0, 0, 0, 1); }

    /** CFrame.lookAt(at, target, up) */
    public static CFrame lookAt(V3 at, V3 target, V3 up) {
        V3 look = target.sub(at).unit();
        if (look.length() < 1e-9) return at(at);
        V3 right = look.cross(up).unit();
        if (right.length() < 1e-9) {
            // looking straight up/down: pick any right vector
            right = look.cross(V3.Z).unit();
            if (right.length() < 1e-9) right = look.cross(V3.X).unit();
        }
        V3 upv = right.cross(look).unit();
        V3 back = look.neg();
        return new CFrame(at.x(), at.y(), at.z(),
                right.x(), upv.x(), back.x(),
                right.y(), upv.y(), back.y(),
                right.z(), upv.z(), back.z());
    }

    public static CFrame lookAt(V3 at, V3 target) { return lookAt(at, target, V3.Y); }

    public static CFrame fromMatrix(V3 pos, V3 vx, V3 vy, V3 vz) {
        return new CFrame(pos.x(), pos.y(), pos.z(), vx.x(), vy.x(), vz.x(), vx.y(), vy.y(), vz.y(), vx.z(), vy.z(), vz.z());
    }

    private static CFrame rx(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new CFrame(0, 0, 0, 1, 0, 0, 0, c, -s, 0, s, c);
    }

    private static CFrame ry(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new CFrame(0, 0, 0, c, 0, s, 0, 1, 0, -s, 0, c);
    }

    private static CFrame rz(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new CFrame(0, 0, 0, c, -s, 0, s, c, 0, 0, 0, 1);
    }

    /** CFrame.Angles / fromEulerAnglesXYZ: R = Rx * Ry * Rz */
    public static CFrame angles(double ax, double ay, double az) {
        return rx(ax).mul(ry(ay)).mul(rz(az));
    }

    /** CFrame.fromOrientation / fromEulerAnglesYXZ: R = Ry * Rx * Rz */
    public static CFrame fromOrientation(double ax, double ay, double az) {
        return ry(ay).mul(rx(ax)).mul(rz(az));
    }

    public static CFrame fromAxisAngle(V3 axis, double angle) {
        V3 a = axis.unit();
        Quaternionf q = new Quaternionf().fromAxisAngleRad((float) a.x(), (float) a.y(), (float) a.z(), (float) angle);
        return fromQuat(0, 0, 0, q.x, q.y, q.z, q.w);
    }

    public static CFrame fromQuat(double px, double py, double pz, double qx, double qy, double qz, double qw) {
        double n = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (n < 1e-12) return at(px, py, pz);
        qx /= n; qy /= n; qz /= n; qw /= n;
        return new CFrame(px, py, pz,
                1 - 2 * (qy * qy + qz * qz), 2 * (qx * qy - qz * qw), 2 * (qx * qz + qy * qw),
                2 * (qx * qy + qz * qw), 1 - 2 * (qx * qx + qz * qz), 2 * (qy * qz - qx * qw),
                2 * (qx * qz - qy * qw), 2 * (qy * qz + qx * qw), 1 - 2 * (qx * qx + qy * qy));
    }

    /** Rotation as quaternion (x, y, z, w). */
    public double[] quat() {
        double tr = r00 + r11 + r22, qw, qx, qy, qz;
        if (tr > 0) {
            double s = Math.sqrt(tr + 1.0) * 2;
            qw = 0.25 * s; qx = (r21 - r12) / s; qy = (r02 - r20) / s; qz = (r10 - r01) / s;
        } else if (r00 > r11 && r00 > r22) {
            double s = Math.sqrt(1.0 + r00 - r11 - r22) * 2;
            qw = (r21 - r12) / s; qx = 0.25 * s; qy = (r01 + r10) / s; qz = (r02 + r20) / s;
        } else if (r11 > r22) {
            double s = Math.sqrt(1.0 + r11 - r00 - r22) * 2;
            qw = (r02 - r20) / s; qx = (r01 + r10) / s; qy = 0.25 * s; qz = (r12 + r21) / s;
        } else {
            double s = Math.sqrt(1.0 + r22 - r00 - r11) * 2;
            qw = (r10 - r01) / s; qx = (r02 + r20) / s; qy = (r12 + r21) / s; qz = 0.25 * s;
        }
        return new double[]{qx, qy, qz, qw};
    }

    public Quaternionf toQuaternionf() {
        double[] q = quat();
        return new Quaternionf((float) q[0], (float) q[1], (float) q[2], (float) q[3]).normalize();
    }

    public CFrame mul(CFrame b) {
        return new CFrame(
                r00 * b.x + r01 * b.y + r02 * b.z + x,
                r10 * b.x + r11 * b.y + r12 * b.z + y,
                r20 * b.x + r21 * b.y + r22 * b.z + z,
                r00 * b.r00 + r01 * b.r10 + r02 * b.r20, r00 * b.r01 + r01 * b.r11 + r02 * b.r21, r00 * b.r02 + r01 * b.r12 + r02 * b.r22,
                r10 * b.r00 + r11 * b.r10 + r12 * b.r20, r10 * b.r01 + r11 * b.r11 + r12 * b.r21, r10 * b.r02 + r11 * b.r12 + r12 * b.r22,
                r20 * b.r00 + r21 * b.r10 + r22 * b.r20, r20 * b.r01 + r21 * b.r11 + r22 * b.r21, r20 * b.r02 + r21 * b.r12 + r22 * b.r22);
    }

    public V3 pointToWorld(V3 v) {
        return new V3(r00 * v.x() + r01 * v.y() + r02 * v.z() + x,
                r10 * v.x() + r11 * v.y() + r12 * v.z() + y,
                r20 * v.x() + r21 * v.y() + r22 * v.z() + z);
    }

    public V3 vectorToWorld(V3 v) {
        return new V3(r00 * v.x() + r01 * v.y() + r02 * v.z(),
                r10 * v.x() + r11 * v.y() + r12 * v.z(),
                r20 * v.x() + r21 * v.y() + r22 * v.z());
    }

    public V3 vectorToObject(V3 v) {
        return new V3(r00 * v.x() + r10 * v.y() + r20 * v.z(),
                r01 * v.x() + r11 * v.y() + r21 * v.z(),
                r02 * v.x() + r12 * v.y() + r22 * v.z());
    }

    public V3 pointToObject(V3 v) { return vectorToObject(v.sub(position())); }

    public CFrame inverse() {
        V3 p = vectorToObject(position()).neg();
        return new CFrame(p.x(), p.y(), p.z(), r00, r10, r20, r01, r11, r21, r02, r12, r22);
    }

    public CFrame toWorldSpace(CFrame c) { return mul(c); }

    public CFrame toObjectSpace(CFrame c) { return inverse().mul(c); }

    public CFrame add(V3 v) { return new CFrame(x + v.x(), y + v.y(), z + v.z(), r00, r01, r02, r10, r11, r12, r20, r21, r22); }

    public CFrame withPosition(V3 v) { return new CFrame(v.x(), v.y(), v.z(), r00, r01, r02, r10, r11, r12, r20, r21, r22); }

    public CFrame rotation() { return withPosition(V3.ZERO); }

    public V3 position() { return new V3(x, y, z); }
    public V3 rightVector() { return new V3(r00, r10, r20); }
    public V3 upVector() { return new V3(r01, r11, r21); }
    public V3 lookVector() { return new V3(-r02, -r12, -r22); }

    /** Slerp rotation, lerp position. */
    public CFrame lerp(CFrame b, double a) {
        double[] q1 = quat(), q2 = b.quat();
        double dot = q1[0] * q2[0] + q1[1] * q2[1] + q1[2] * q2[2] + q1[3] * q2[3];
        if (dot < 0) { dot = -dot; for (int i = 0; i < 4; i++) q2[i] = -q2[i]; }
        double s0, s1;
        if (dot > 0.9995) {
            s0 = 1 - a; s1 = a;
        } else {
            double th = Math.acos(dot), sn = Math.sin(th);
            s0 = Math.sin((1 - a) * th) / sn;
            s1 = Math.sin(a * th) / sn;
        }
        return fromQuat(x + (b.x - x) * a, y + (b.y - y) * a, z + (b.z - z) * a,
                q1[0] * s0 + q2[0] * s1, q1[1] * s0 + q2[1] * s1, q1[2] * s0 + q2[2] * s1, q1[3] * s0 + q2[3] * s1);
    }

    /** Returns rx, ry, rz such that R = Rx * Ry * Rz */
    public double[] toEulerXYZ() {
        double ry = Math.asin(Math.max(-1, Math.min(1, r02)));
        double rx, rz;
        if (Math.abs(r02) < 0.99999) {
            rx = Math.atan2(-r12, r22);
            rz = Math.atan2(-r01, r00);
        } else {
            rx = Math.atan2(r21, r11);
            rz = 0;
        }
        return new double[]{rx, ry, rz};
    }

    /** Returns rx, ry, rz such that R = Ry * Rx * Rz (Roblox Orientation, in radians) */
    public double[] toOrientation() {
        double rx = Math.asin(Math.max(-1, Math.min(1, -r12)));
        double ry, rz;
        if (Math.abs(r12) < 0.99999) {
            ry = Math.atan2(r02, r22);
            rz = Math.atan2(r10, r11);
        } else {
            ry = Math.atan2(-r20, r00);
            rz = 0;
        }
        return new double[]{rx, ry, rz};
    }

    /** Orientation in degrees, like BasePart.Orientation */
    public V3 orientationDegrees() {
        double[] o = toOrientation();
        return new V3(Math.toDegrees(o[0]), Math.toDegrees(o[1]), Math.toDegrees(o[2]));
    }

    public static CFrame fromOrientationDegrees(V3 pos, V3 deg) {
        return fromOrientation(Math.toRadians(deg.x()), Math.toRadians(deg.y()), Math.toRadians(deg.z())).withPosition(pos);
    }

    public double[] components() {
        return new double[]{x, y, z, r00, r01, r02, r10, r11, r12, r20, r21, r22};
    }

    /** Minecraft yaw (degrees) that faces this CFrame's LookVector. */
    public float mcYaw() {
        V3 l = lookVector();
        return (float) Math.toDegrees(Math.atan2(-l.x(), l.z()));
    }

    /** CFrame for a Minecraft yaw (degrees) around the Y axis. */
    public static CFrame fromMcYaw(V3 pos, float yawDeg) {
        double t = Math.toRadians(yawDeg);
        V3 look = new V3(-Math.sin(t), 0, Math.cos(t));
        return lookAt(pos, pos.add(look));
    }

    /** true if the rotation maps axes onto axes (multiples of 90 degrees) */
    public boolean isAxisAligned() {
        double[] m = {r00, r01, r02, r10, r11, r12, r20, r21, r22};
        for (double d : m) {
            double a = Math.abs(d);
            if (a > 1e-4 && Math.abs(a - 1) > 1e-4) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CFrame c)) return false;
        double[] a = components(), b = c.components();
        for (int i = 0; i < 12; i++) if (a[i] != b[i]) return false;
        return true;
    }

    @Override
    public int hashCode() { return java.util.Arrays.hashCode(components()); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        double[] c = components();
        for (int i = 0; i < 12; i++) {
            if (i > 0) sb.append(", ");
            sb.append(V3.num(c[i]));
        }
        return sb.toString();
    }
}

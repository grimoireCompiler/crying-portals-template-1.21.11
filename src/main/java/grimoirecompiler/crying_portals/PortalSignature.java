package grimoirecompiler.crying_portals;

/**
 * Builds the mirror-invariant signature of a Crying Portal frame.
 * <p>
 * Each edge excludes the corners and is given in "viewer" order: base and top from left to right,
 * left and right columns from bottom to top. A value of {@code true} means Crying Obsidian.
 * <p>
 * The frame is read as a clockwise loop (base L→R, right B→T, top R→L, left T→B). The mirrored frame
 * (seen from the other side) yields a second loop; the canonical loop is the lexicographically smaller one,
 * so both sides of the portal and X/Z rotations produce the same signature.
 */
public final class PortalSignature {
    private PortalSignature() {}

    public static String compute(boolean[] base, boolean[] top, boolean[] left, boolean[] right) {
        int width = base.length;
        int height = left.length;
        if (top.length != width || right.length != height) {
            throw new IllegalArgumentException("Opposite frame edges must have the same length");
        }

        boolean[] loop = loop(base, top, left, right);
        boolean[] mirrored = loop(reversed(base), reversed(top), right, left);
        boolean[] canonical = compare(loop, mirrored) <= 0 ? loop : mirrored;

        return width + "x" + height + ":" + toHex(canonical);
    }

    public static boolean hasCrying(boolean[]... edges) {
        for (boolean[] edge : edges)
            for (boolean b : edge)
                if (b) return true;
        return false;
    }

    private static boolean[] loop(boolean[] base, boolean[] top, boolean[] left, boolean[] right) {
        int width = base.length;
        int height = left.length;
        boolean[] loop = new boolean[2 * (width + height)];
        int i = 0;
        for (int x = 0; x < width; x++) loop[i++] = base[x];
        for (int y = 0; y < height; y++) loop[i++] = right[y];
        for (int x = width - 1; x >= 0; x--) loop[i++] = top[x];
        for (int y = height - 1; y >= 0; y--) loop[i++] = left[y];
        return loop;
    }

    private static boolean[] reversed(boolean[] array) {
        boolean[] result = new boolean[array.length];
        for (int i = 0; i < array.length; i++) result[i] = array[array.length - 1 - i];
        return result;
    }

    private static int compare(boolean[] a, boolean[] b) {
        for (int i = 0; i < a.length; i++)
            if (a[i] != b[i]) return a[i] ? 1 : -1;
        return 0;
    }

    private static String toHex(boolean[] bits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bits.length; i += 4) {
            int nibble = 0;
            for (int j = 0; j < 4; j++) {
                nibble <<= 1;
                if (i + j < bits.length && bits[i + j]) nibble |= 1;
            }
            sb.append(Character.forDigit(nibble, 16));
        }
        return sb.toString();
    }
}

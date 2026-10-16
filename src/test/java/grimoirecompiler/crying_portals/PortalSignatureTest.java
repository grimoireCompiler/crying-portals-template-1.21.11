package grimoirecompiler.crying_portals;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PortalSignatureTest {
    private static final boolean C = true, O = false;

    private static boolean[] b(boolean... values) {
        return values;
    }

    private static boolean[] reversed(boolean[] array) {
        boolean[] result = new boolean[array.length];
        for (int i = 0; i < array.length; i++) result[i] = array[array.length - 1 - i];
        return result;
    }

    /** The same frame seen from the other side: rows reversed, side columns swapped. */
    private static String mirrored(boolean[] base, boolean[] top, boolean[] left, boolean[] right) {
        return PortalSignature.compute(reversed(base), reversed(top), right, left);
    }

    @Test
    void mirroredFrameHasSameSignature() {
        boolean[] base = b(C, O), top = b(O, O), left = b(O, C, O), right = b(O, O, C);
        assertEquals(PortalSignature.compute(base, top, left, right), mirrored(base, top, left, right));
    }

    @Test
    void mirroredAsymmetricBaseOnly() {
        boolean[] base = b(C, O, O), top = b(O, O, O), left = b(O, O, O), right = b(O, O, O);
        assertEquals(PortalSignature.compute(base, top, left, right), mirrored(base, top, left, right));
    }

    @Test
    void mirroredWhenOnlyColumnsDiffer() {
        boolean[] base = b(O, O), top = b(C, C), left = b(C, O, O, O), right = b(O, O, O, C);
        assertEquals(PortalSignature.compute(base, top, left, right), mirrored(base, top, left, right));
    }

    @Test
    void symmetricFrame() {
        boolean[] base = b(C, O, C), top = b(O, C, O), left = b(C, O, C), right = b(C, O, C);
        assertEquals(PortalSignature.compute(base, top, left, right), mirrored(base, top, left, right));
    }

    @Test
    void differentPatternsDiffer() {
        String a = PortalSignature.compute(b(C, O), b(O, O), b(O, O, O), b(O, O, O));
        String b = PortalSignature.compute(b(O, O), b(C, O), b(O, O, O), b(O, O, O));
        String c = PortalSignature.compute(b(O, O), b(O, O), b(O, C, O), b(O, O, O));
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(b, c);
    }

    @Test
    void upsideDownIsNotTheSame() {
        // Only mirroring is tolerated, flipping the frame vertically is a different pattern
        String a = PortalSignature.compute(b(C, C), b(O, O), b(O, O, O), b(O, O, O));
        String b = PortalSignature.compute(b(O, O), b(C, C), b(O, O, O), b(O, O, O));
        assertNotEquals(a, b);
    }

    @Test
    void sizeIsPartOfSignature() {
        // 2x4 and 3x3 both have a 12 block loop; same loop bits must still differ
        String a = PortalSignature.compute(b(C, O), b(O, O), b(O, O, O, O), b(O, O, O, O));
        String b = PortalSignature.compute(b(C, O, O), b(O, O, O), b(O, O, O), b(O, O, O));
        assertNotEquals(a, b);
        assertTrue(a.startsWith("2x4:"));
        assertTrue(b.startsWith("3x3:"));
    }

    @Test
    void hasCrying() {
        assertFalse(PortalSignature.hasCrying(b(O, O), b(O, O), b(O, O, O), b(O, O, O)));
        assertTrue(PortalSignature.hasCrying(b(O, O), b(O, O), b(O, O, O), b(O, O, C)));
    }

    @Test
    void mismatchedEdgesRejected() {
        assertThrows(IllegalArgumentException.class, () -> PortalSignature.compute(b(O, O), b(O), b(O, O, O), b(O, O, O)));
    }
}

package com.termux.view;

import junit.framework.TestCase;

public class BlockGlyphsTest extends TestCase {

    public void testOctants() {
        assertGlyph(2, 4, 0b00000100, false, 0x1CD00);
        assertGlyph(2, 4, 0b11111110, false, 0x1CDE5);
        assertGlyph(2, 4, 0b00010100, false, 0x1FBE6);
        assertGlyph(2, 4, 0b00101000, false, 0x1FBE7);
    }

    public void testSextants() {
        assertGlyph(2, 3, 0b000001, false, 0x1FB00);
        assertGlyph(2, 3, 0b010110, false, 0x1FB14);
        assertGlyph(2, 3, 0b101011, false, 0x1FB28);
    }

    public void testSeparatedSextants() {
        assertGlyph(2, 3, 0b000001, true, 0x1CE51);
        assertGlyph(2, 3, 0b111111, true, 0x1CE8F);
    }

    public void testEighthAndFractionalBlocks() {
        assertGlyph(8, 1, 0b00000010, false, 0x1FB70);
        assertGlyph(8, 1, 0b01000000, false, 0x1FB75);
        assertGlyph(1, 8, 0b00000010, false, 0x1FB76);
        assertGlyph(1, 8, 0b01000000, false, 0x1FB7B);
        assertGlyph(1, 8, 0b00000011, false, 0x1FB82);
        assertGlyph(1, 8, 0b01111111, false, 0x1FB86);
        assertGlyph(8, 1, 0b11000000, false, 0x1FB87);
        assertGlyph(8, 1, 0b11111110, false, 0x1FB8B);
    }

    public void testQuarterBlockPartials() {
        assertGlyph(4, 4, (1 << 14) | (1 << 15), false, 0x1CEA0);
        assertGlyph(4, 4, (1 << 12) | (1 << 13), false, 0x1CEA3);
        assertGlyph(4, 4, (1 << 0) | (1 << 1), false, 0x1CEA8);
        assertGlyph(4, 4, (1 << 2) | (1 << 3), false, 0x1CEAB);
        assertGlyph(4, 4, (1 << 11) | (1 << 15), false, 0x1CEAF);
    }

    public void testUnsupportedGlyphs() {
        assertEquals(BlockGlyphs.UNSUPPORTED, BlockGlyphs.getSpec(0x1CDE6));
        assertEquals(BlockGlyphs.UNSUPPORTED, BlockGlyphs.getSpec(0x1FB3C));
        assertEquals(BlockGlyphs.UNSUPPORTED, BlockGlyphs.getSpec('A'));
    }

    private static void assertGlyph(int columns, int rows, int mask, boolean separated, int codePoint) {
        int spec = BlockGlyphs.getSpec(codePoint);
        assertEquals(columns, BlockGlyphs.getColumns(spec));
        assertEquals(rows, BlockGlyphs.getRows(spec));
        assertEquals(mask, BlockGlyphs.getMask(spec));
        assertEquals(separated, BlockGlyphs.isSeparated(spec));
    }

}

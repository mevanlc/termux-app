package com.termux.view;

final class BlockGlyphs {

    static final int UNSUPPORTED = 0;

    private static final int MASK_SHIFT = 0;
    private static final int COLUMNS_SHIFT = 16;
    private static final int ROWS_SHIFT = 20;
    private static final int SEPARATED_SHIFT = 24;

    private static final int OCTANT_MASK = 0xff;

    private static final int OCTANT_START = 0x1CD00;
    private static final int OCTANT_END = 0x1CDE5;
    private static final int OCTANT_LEGACY_LEFT = 0x1FBE6;
    private static final int OCTANT_LEGACY_RIGHT = 0x1FBE7;

    private static final int SEXTANT_RANGE_1_START = 0x1FB00;
    private static final int SEXTANT_RANGE_1_END = SEXTANT_RANGE_1_START + 19;
    private static final int SEXTANT_RANGE_2_START = 0x1FB14;
    private static final int SEXTANT_RANGE_2_END = SEXTANT_RANGE_2_START + 19;
    private static final int SEXTANT_RANGE_3_START = 0x1FB28;
    private static final int SEXTANT_RANGE_3_END = SEXTANT_RANGE_3_START + 19;
    private static final int VERTICAL_ONE_EIGHTH_START = 0x1FB70;
    private static final int VERTICAL_ONE_EIGHTH_END = VERTICAL_ONE_EIGHTH_START + 5;
    private static final int HORIZONTAL_ONE_EIGHTH_START = 0x1FB76;
    private static final int HORIZONTAL_ONE_EIGHTH_END = HORIZONTAL_ONE_EIGHTH_START + 5;
    private static final int UPPER_FRACTIONAL_BLOCK_START = 0x1FB82;
    private static final int UPPER_FRACTIONAL_BLOCK_END = UPPER_FRACTIONAL_BLOCK_START + 4;
    private static final int RIGHT_FRACTIONAL_BLOCK_START = 0x1FB87;
    private static final int RIGHT_FRACTIONAL_BLOCK_END = RIGHT_FRACTIONAL_BLOCK_START + 4;
    private static final int QUARTER_BLOCK_PARTIAL_START = 0x1CEA0;
    private static final int QUARTER_BLOCK_PARTIAL_END = QUARTER_BLOCK_PARTIAL_START + 15;
    private static final int SEPARATED_SEXTANT_START = 0x1CE51;
    private static final int SEPARATED_SEXTANT_END = SEPARATED_SEXTANT_START + 62;

    private static final int OCTANT_A = 1;
    private static final int OCTANT_B = 2;
    private static final int OCTANT_C = 4;
    private static final int OCTANT_D = 8;
    private static final int OCTANT_M = 16;
    private static final int OCTANT_N = 32;
    private static final int OCTANT_O = 64;
    private static final int OCTANT_P = 128;

    private static final int[] UPPER_FRACTIONAL_BLOCK_MASKS = {
        0b00000011, 0b00000111, 0b00011111, 0b00111111, 0b01111111
    };

    private static final int[] RIGHT_FRACTIONAL_BLOCK_MASKS = {
        0b11000000, 0b11100000, 0b11111000, 0b11111100, 0b11111110
    };

    private static final int[] QUARTER_BLOCK_PARTIAL_MASKS = {
        (1 << 14) | (1 << 15),
        (1 << 13) | (1 << 14) | (1 << 15),
        (1 << 12) | (1 << 13) | (1 << 14),
        (1 << 12) | (1 << 13),
        (1 << 8) | (1 << 12),
        (1 << 4) | (1 << 8) | (1 << 12),
        (1 << 0) | (1 << 4) | (1 << 8),
        (1 << 0) | (1 << 4),
        (1 << 0) | (1 << 1),
        (1 << 0) | (1 << 1) | (1 << 2),
        (1 << 1) | (1 << 2) | (1 << 3),
        (1 << 2) | (1 << 3),
        (1 << 3) | (1 << 7),
        (1 << 3) | (1 << 7) | (1 << 11),
        (1 << 7) | (1 << 11) | (1 << 15),
        (1 << 11) | (1 << 15),
    };

    private static final int[] OCTANT_MAPPING = {
        // 00 - 0f
        OCTANT_B, OCTANT_B | OCTANT_M, OCTANT_A | OCTANT_B | OCTANT_M, OCTANT_N,
        OCTANT_A | OCTANT_N, OCTANT_A | OCTANT_M | OCTANT_N, OCTANT_B | OCTANT_N, OCTANT_A | OCTANT_B | OCTANT_N,
        OCTANT_B | OCTANT_M | OCTANT_N, OCTANT_C, OCTANT_A | OCTANT_C, OCTANT_C | OCTANT_M,
        OCTANT_A | OCTANT_C | OCTANT_M, OCTANT_A | OCTANT_B | OCTANT_C, OCTANT_B | OCTANT_C | OCTANT_M, OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_M,
        // 10 - 1f
        OCTANT_C | OCTANT_N, OCTANT_A | OCTANT_C | OCTANT_N, OCTANT_C | OCTANT_M | OCTANT_N, OCTANT_A | OCTANT_C | OCTANT_M | OCTANT_N,
        OCTANT_B | OCTANT_C | OCTANT_N, OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_N, OCTANT_B | OCTANT_C | OCTANT_M | OCTANT_N, OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_M | OCTANT_N,
        OCTANT_O, OCTANT_A | OCTANT_O, OCTANT_M | OCTANT_O, OCTANT_A | OCTANT_M | OCTANT_O,
        OCTANT_B | OCTANT_O, OCTANT_A | OCTANT_B | OCTANT_O, OCTANT_B | OCTANT_M | OCTANT_O, OCTANT_A | OCTANT_B | OCTANT_M | OCTANT_O,
        // 20 - 2f
        OCTANT_A | OCTANT_N | OCTANT_O, OCTANT_M | OCTANT_N | OCTANT_O, OCTANT_A | OCTANT_M | OCTANT_N | OCTANT_O, OCTANT_B | OCTANT_N | OCTANT_O,
        OCTANT_A | OCTANT_B | OCTANT_N | OCTANT_O, OCTANT_B | OCTANT_M | OCTANT_N | OCTANT_O, OCTANT_A | OCTANT_B | OCTANT_M | OCTANT_N | OCTANT_O, OCTANT_C | OCTANT_O,
        OCTANT_A | OCTANT_C | OCTANT_O, OCTANT_C | OCTANT_M | OCTANT_O, OCTANT_A | OCTANT_C | OCTANT_M | OCTANT_O, OCTANT_B | OCTANT_C | OCTANT_O,
        OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_O, OCTANT_B | OCTANT_C | OCTANT_M | OCTANT_O, OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_M | OCTANT_O, OCTANT_C | OCTANT_N | OCTANT_O,
        // 30 - 3f
        OCTANT_A | OCTANT_C | OCTANT_N | OCTANT_O, OCTANT_C | OCTANT_M | OCTANT_N | OCTANT_O, OCTANT_A | OCTANT_C | OCTANT_M | OCTANT_N | OCTANT_O, OCTANT_B | OCTANT_C | OCTANT_N | OCTANT_O,
        OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_N | OCTANT_O, OCTANT_B | OCTANT_C | OCTANT_M | OCTANT_N | OCTANT_O, OCTANT_A | OCTANT_D, OCTANT_D | OCTANT_M,
        OCTANT_A | OCTANT_D | OCTANT_M, OCTANT_B | OCTANT_D, OCTANT_A | OCTANT_B | OCTANT_D, OCTANT_B | OCTANT_D | OCTANT_M,
        OCTANT_A | OCTANT_B | OCTANT_D | OCTANT_M, OCTANT_D | OCTANT_N, OCTANT_A | OCTANT_D | OCTANT_N, OCTANT_D | OCTANT_M | OCTANT_N,
        // 40 - 4f
        OCTANT_A | OCTANT_D | OCTANT_M | OCTANT_N, OCTANT_B | OCTANT_D | OCTANT_N, OCTANT_A | OCTANT_B | OCTANT_D | OCTANT_N, OCTANT_B | OCTANT_D | OCTANT_M | OCTANT_N,
        OCTANT_A | OCTANT_B | OCTANT_D | OCTANT_M | OCTANT_N, OCTANT_A | OCTANT_C | OCTANT_D, OCTANT_C | OCTANT_D | OCTANT_M, OCTANT_A | OCTANT_C | OCTANT_D | OCTANT_M,
        OCTANT_B | OCTANT_C | OCTANT_D, OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_M, OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_M, OCTANT_C | OCTANT_D | OCTANT_N,
        OCTANT_A | OCTANT_C | OCTANT_D | OCTANT_N, OCTANT_A | OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_N, OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_N, OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_N,
        // 50 - 5f
        OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_N, OCTANT_D | OCTANT_O, OCTANT_A | OCTANT_D | OCTANT_O, OCTANT_D | OCTANT_M | OCTANT_O,
        OCTANT_A | OCTANT_D | OCTANT_M | OCTANT_O, OCTANT_B | OCTANT_D | OCTANT_O, OCTANT_A | OCTANT_B | OCTANT_D | OCTANT_O, OCTANT_B | OCTANT_D | OCTANT_M | OCTANT_O,
        OCTANT_A | OCTANT_B | OCTANT_D | OCTANT_M | OCTANT_O, OCTANT_D | OCTANT_N | OCTANT_O, OCTANT_A | OCTANT_D | OCTANT_N | OCTANT_O, OCTANT_D | OCTANT_M | OCTANT_N | OCTANT_O,
        OCTANT_A | OCTANT_D | OCTANT_M | OCTANT_N | OCTANT_O, OCTANT_B | OCTANT_D | OCTANT_N | OCTANT_O, OCTANT_A | OCTANT_B | OCTANT_D | OCTANT_N | OCTANT_O, OCTANT_B | OCTANT_D | OCTANT_M | OCTANT_N | OCTANT_O,
        // 60 - 6f
        invert(OCTANT_C | OCTANT_P), OCTANT_C | OCTANT_D | OCTANT_O, OCTANT_A | OCTANT_C | OCTANT_D | OCTANT_O, OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_O,
        OCTANT_A | OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_O, OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_O, invert(OCTANT_M | OCTANT_N | OCTANT_P), OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_O,
        invert(OCTANT_N | OCTANT_P), OCTANT_C | OCTANT_D | OCTANT_N | OCTANT_O, OCTANT_A | OCTANT_C | OCTANT_D | OCTANT_N | OCTANT_O, OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_N | OCTANT_O,
        invert(OCTANT_B | OCTANT_P), OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_N | OCTANT_O, invert(OCTANT_M | OCTANT_P), invert(OCTANT_A | OCTANT_P),
        // 70 - 7f
        invert(OCTANT_P), OCTANT_A | OCTANT_P, OCTANT_M | OCTANT_P, OCTANT_A | OCTANT_M | OCTANT_P,
        OCTANT_B | OCTANT_P, OCTANT_A | OCTANT_B | OCTANT_P, OCTANT_B | OCTANT_M | OCTANT_P, OCTANT_A | OCTANT_B | OCTANT_M | OCTANT_P,
        OCTANT_N | OCTANT_P, OCTANT_A | OCTANT_N | OCTANT_P, OCTANT_M | OCTANT_N | OCTANT_P, OCTANT_A | OCTANT_M | OCTANT_N | OCTANT_P,
        OCTANT_B | OCTANT_N | OCTANT_P, OCTANT_A | OCTANT_B | OCTANT_N | OCTANT_P, OCTANT_B | OCTANT_M | OCTANT_N | OCTANT_P, invert(OCTANT_C | OCTANT_D | OCTANT_O),
        // 80 - 8f
        OCTANT_C | OCTANT_P, OCTANT_A | OCTANT_C | OCTANT_P, OCTANT_C | OCTANT_M | OCTANT_P, OCTANT_A | OCTANT_C | OCTANT_M | OCTANT_P,
        OCTANT_B | OCTANT_C | OCTANT_P, OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_P, OCTANT_B | OCTANT_C | OCTANT_M | OCTANT_P, invert(OCTANT_D | OCTANT_N | OCTANT_O),
        OCTANT_C | OCTANT_N | OCTANT_P, OCTANT_A | OCTANT_C | OCTANT_N | OCTANT_P, OCTANT_C | OCTANT_M | OCTANT_N | OCTANT_P, invert(OCTANT_B | OCTANT_D | OCTANT_O),
        OCTANT_B | OCTANT_C | OCTANT_N | OCTANT_P, invert(OCTANT_D | OCTANT_M | OCTANT_O), invert(OCTANT_A | OCTANT_D | OCTANT_O), invert(OCTANT_D | OCTANT_O),
        // 90 - 9f
        OCTANT_A | OCTANT_O | OCTANT_P, OCTANT_M | OCTANT_O | OCTANT_P, OCTANT_A | OCTANT_M | OCTANT_O | OCTANT_P, OCTANT_B | OCTANT_O | OCTANT_P,
        OCTANT_B | OCTANT_M | OCTANT_O | OCTANT_P, OCTANT_A | OCTANT_B | OCTANT_M | OCTANT_O | OCTANT_P, OCTANT_N | OCTANT_O | OCTANT_P, OCTANT_A | OCTANT_N | OCTANT_O | OCTANT_P,
        OCTANT_A | OCTANT_M | OCTANT_N | OCTANT_O | OCTANT_P, OCTANT_B | OCTANT_N | OCTANT_O | OCTANT_P, OCTANT_A | OCTANT_B | OCTANT_N | OCTANT_O | OCTANT_P, OCTANT_B | OCTANT_M | OCTANT_N | OCTANT_O | OCTANT_P,
        OCTANT_C | OCTANT_O | OCTANT_P, OCTANT_A | OCTANT_C | OCTANT_O | OCTANT_P, OCTANT_C | OCTANT_M | OCTANT_O | OCTANT_P, OCTANT_A | OCTANT_C | OCTANT_M | OCTANT_O | OCTANT_P,
        // a0 - af
        OCTANT_B | OCTANT_C | OCTANT_O | OCTANT_P, OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_O | OCTANT_P, OCTANT_B | OCTANT_C | OCTANT_M | OCTANT_O | OCTANT_P, invert(OCTANT_N | OCTANT_D),
        OCTANT_C | OCTANT_N | OCTANT_O | OCTANT_P, OCTANT_A | OCTANT_C | OCTANT_N | OCTANT_O | OCTANT_P, OCTANT_C | OCTANT_M | OCTANT_N | OCTANT_O | OCTANT_P, invert(OCTANT_B | OCTANT_D),
        OCTANT_B | OCTANT_C | OCTANT_N | OCTANT_O | OCTANT_P, invert(OCTANT_D | OCTANT_M), invert(OCTANT_A | OCTANT_D), invert(OCTANT_D),
        OCTANT_A | OCTANT_D | OCTANT_P, OCTANT_D | OCTANT_M | OCTANT_P, OCTANT_A | OCTANT_D | OCTANT_M | OCTANT_P, OCTANT_B | OCTANT_D | OCTANT_P,
        // b0 - bf
        OCTANT_A | OCTANT_B | OCTANT_D | OCTANT_P, OCTANT_B | OCTANT_D | OCTANT_M | OCTANT_P, OCTANT_A | OCTANT_B | OCTANT_D | OCTANT_M | OCTANT_P, OCTANT_D | OCTANT_N | OCTANT_P,
        OCTANT_A | OCTANT_D | OCTANT_N | OCTANT_P, OCTANT_D | OCTANT_M | OCTANT_N | OCTANT_P, OCTANT_A | OCTANT_D | OCTANT_M | OCTANT_N | OCTANT_P, OCTANT_B | OCTANT_D | OCTANT_N | OCTANT_P,
        OCTANT_A | OCTANT_B | OCTANT_D | OCTANT_N | OCTANT_P, OCTANT_B | OCTANT_D | OCTANT_M | OCTANT_N | OCTANT_P, invert(OCTANT_C | OCTANT_O), OCTANT_C | OCTANT_D | OCTANT_P,
        OCTANT_A | OCTANT_C | OCTANT_D | OCTANT_P, OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_P, OCTANT_A | OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_P, OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_P,
        // c0 - cf
        OCTANT_A | OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_P, OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_P, invert(OCTANT_N | OCTANT_O), OCTANT_C | OCTANT_D | OCTANT_N | OCTANT_P,
        OCTANT_A | OCTANT_C | OCTANT_D | OCTANT_N | OCTANT_P, OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_N | OCTANT_P, invert(OCTANT_B | OCTANT_O), OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_N | OCTANT_P,
        invert(OCTANT_M | OCTANT_O), invert(OCTANT_A | OCTANT_O), invert(OCTANT_O), OCTANT_D | OCTANT_O | OCTANT_P,
        OCTANT_A | OCTANT_D | OCTANT_O | OCTANT_P, OCTANT_D | OCTANT_M | OCTANT_O | OCTANT_P, OCTANT_A | OCTANT_D | OCTANT_M | OCTANT_O | OCTANT_P, OCTANT_B | OCTANT_D | OCTANT_O | OCTANT_P,
        // d0 - df
        OCTANT_A | OCTANT_B | OCTANT_D | OCTANT_O | OCTANT_P, OCTANT_B | OCTANT_D | OCTANT_M | OCTANT_O | OCTANT_P, invert(OCTANT_C | OCTANT_N), OCTANT_D | OCTANT_N | OCTANT_O | OCTANT_P,
        OCTANT_A | OCTANT_D | OCTANT_N | OCTANT_O | OCTANT_P, OCTANT_D | OCTANT_M | OCTANT_N | OCTANT_O | OCTANT_P, invert(OCTANT_B | OCTANT_C), OCTANT_B | OCTANT_D | OCTANT_N | OCTANT_O | OCTANT_P,
        invert(OCTANT_C | OCTANT_M), invert(OCTANT_A | OCTANT_C), invert(OCTANT_C), OCTANT_A | OCTANT_C | OCTANT_D | OCTANT_O | OCTANT_P,
        OCTANT_C | OCTANT_D | OCTANT_M | OCTANT_O | OCTANT_P, invert(OCTANT_B | OCTANT_N), OCTANT_B | OCTANT_C | OCTANT_D | OCTANT_O | OCTANT_P, invert(OCTANT_A | OCTANT_N),
        // e0 - e7
        invert(OCTANT_N), OCTANT_C | OCTANT_D | OCTANT_N | OCTANT_O | OCTANT_P, invert(OCTANT_B | OCTANT_M), invert(OCTANT_B),
        invert(OCTANT_M), invert(OCTANT_A), OCTANT_B | OCTANT_C, OCTANT_N | OCTANT_O,
    };

    private BlockGlyphs() {
    }

    static int getSpec(int codePoint) {
        if (codePoint >= OCTANT_START && codePoint <= OCTANT_END)
            return pack(2, 4, octantMaskToRowMajor(OCTANT_MAPPING[codePoint - OCTANT_START]), false);

        if (codePoint == OCTANT_LEGACY_LEFT)
            return pack(2, 4, octantMaskToRowMajor(OCTANT_MAPPING[0xe6]), false);

        if (codePoint == OCTANT_LEGACY_RIGHT)
            return pack(2, 4, octantMaskToRowMajor(OCTANT_MAPPING[0xe7]), false);

        if (codePoint >= SEXTANT_RANGE_1_START && codePoint <= SEXTANT_RANGE_1_END)
            return pack(2, 3, codePoint - SEXTANT_RANGE_1_START + 1, false);

        if (codePoint >= SEXTANT_RANGE_2_START && codePoint <= SEXTANT_RANGE_2_END)
            return pack(2, 3, codePoint - SEXTANT_RANGE_1_START + 2, false);

        if (codePoint >= SEXTANT_RANGE_3_START && codePoint <= SEXTANT_RANGE_3_END)
            return pack(2, 3, codePoint - SEXTANT_RANGE_1_START + 3, false);

        if (codePoint >= VERTICAL_ONE_EIGHTH_START && codePoint <= VERTICAL_ONE_EIGHTH_END)
            return pack(8, 1, 1 << (codePoint - VERTICAL_ONE_EIGHTH_START + 1), false);

        if (codePoint >= HORIZONTAL_ONE_EIGHTH_START && codePoint <= HORIZONTAL_ONE_EIGHTH_END)
            return pack(1, 8, 1 << (codePoint - HORIZONTAL_ONE_EIGHTH_START + 1), false);

        if (codePoint >= UPPER_FRACTIONAL_BLOCK_START && codePoint <= UPPER_FRACTIONAL_BLOCK_END)
            return pack(1, 8, UPPER_FRACTIONAL_BLOCK_MASKS[codePoint - UPPER_FRACTIONAL_BLOCK_START], false);

        if (codePoint >= RIGHT_FRACTIONAL_BLOCK_START && codePoint <= RIGHT_FRACTIONAL_BLOCK_END)
            return pack(8, 1, RIGHT_FRACTIONAL_BLOCK_MASKS[codePoint - RIGHT_FRACTIONAL_BLOCK_START], false);

        if (codePoint >= QUARTER_BLOCK_PARTIAL_START && codePoint <= QUARTER_BLOCK_PARTIAL_END)
            return pack(4, 4, QUARTER_BLOCK_PARTIAL_MASKS[codePoint - QUARTER_BLOCK_PARTIAL_START], false);

        if (codePoint >= SEPARATED_SEXTANT_START && codePoint <= SEPARATED_SEXTANT_END)
            return pack(2, 3, codePoint - SEPARATED_SEXTANT_START + 1, true);

        return UNSUPPORTED;
    }

    static int getMask(int spec) {
        return (spec >> MASK_SHIFT) & 0xffff;
    }

    static int getColumns(int spec) {
        return (spec >> COLUMNS_SHIFT) & 0xf;
    }

    static int getRows(int spec) {
        return (spec >> ROWS_SHIFT) & 0xf;
    }

    static boolean isSeparated(int spec) {
        return ((spec >> SEPARATED_SHIFT) & 1) != 0;
    }

    private static int pack(int columns, int rows, int mask, boolean separated) {
        return (mask << MASK_SHIFT) | (columns << COLUMNS_SHIFT) | (rows << ROWS_SHIFT) | (separated ? (1 << SEPARATED_SHIFT) : 0);
    }

    private static int octantMaskToRowMajor(int octantMask) {
        int mask = 0;
        for (int row = 0; row < 4; row++) {
            if ((octantMask & (1 << row)) != 0)
                mask |= 1 << (row * 2);
            if ((octantMask & (1 << (row + 4))) != 0)
                mask |= 1 << (row * 2 + 1);
        }
        return mask;
    }

    private static int invert(int mask) {
        return mask ^ OCTANT_MASK;
    }

}

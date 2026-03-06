package com.feel.gems.adventure;

/** Stable campaign identifiers. Bit values are save data and must not change. */
public enum LandmarkKey {
    FRAMEFALL("framefall_gallery", "Framefall Gallery", 1 << 0),
    LASTLIGHT("lastlight_exchange", "Lastlight Exchange", 1 << 1),
    REHEARSAL("rehearsal_works", "Rehearsal Works", 1 << 2),
    BRANCH("branch_vault", "Branch Vault", 1 << 3),
    REDLINE("redline_house", "Redline House", 1 << 4),
    HUSHWIRE("hushwire", "Hushwire", 1 << 5),
    UNWRITTEN("unwritten_court", "Unwritten Court", 1 << 6);

    public static final int ALL_MASK = (1 << 7) - 1;

    private final String id;
    private final String label;
    private final int bit;

    LandmarkKey(String id, String label, int bit) {
        this.id = id;
        this.label = label;
        this.bit = bit;
    }

    public String id() {
        return id;
    }

    public int bit() {
        return bit;
    }

    public String label() {
        return label;
    }
}

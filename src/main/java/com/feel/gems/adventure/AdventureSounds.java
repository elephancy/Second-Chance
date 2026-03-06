package com.feel.gems.adventure;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Campaign sound events live in the Second Chance namespace. */
public final class AdventureSounds {
    public static final SoundEvent STORY_PAGE_TURN = register("story_page_turn");
    public static final SoundEvent STORY_INK_WRITE = register("story_ink_write");
    public static final SoundEvent STORY_RECORD_SEAL = register("story_record_seal");
    public static final SoundEvent CREATURE_PAPER = register("creature_paper");
    public static final SoundEvent CREATURE_CLOCKWORK = register("creature_clockwork");
    public static final SoundEvent CREATURE_BREATH = register("creature_breath");
    public static final SoundEvent CREATURE_STONE = register("creature_stone");
    public static final SoundEvent CREATURE_REED = register("creature_reed");
    public static final SoundEvent VERDICT_AIM = register("verdict_aim");
    public static final SoundEvent VERDICT_RELEASE = register("verdict_release");
    public static final SoundEvent HUSHWIRE_FALSE = register("hushwire_false");
    public static final SoundEvent HUSHWIRE_TRUE = register("hushwire_true");
    public static final SoundEvent HUSHWIRE_PULSE = register("hushwire_pulse");
    public static final SoundEvent CADENZA_CALL = register("cadenza_call");
    public static final SoundEvent CADENZA_LOCK = register("cadenza_lock");
    public static final SoundEvent CADENZA_STRIKE = register("cadenza_strike");
    public static final SoundEvent LASTLIGHT_CALL = register("lastlight_call");
    public static final SoundEvent LASTLIGHT_LOCK = register("lastlight_lock");
    public static final SoundEvent LASTLIGHT_STRIKE = register("lastlight_strike");
    public static final SoundEvent REHEARSAL_CALL = register("rehearsal_call");
    public static final SoundEvent REHEARSAL_LOCK = register("rehearsal_lock");
    public static final SoundEvent REHEARSAL_STRIKE = register("rehearsal_strike");
    public static final SoundEvent BRANCH_CALL = register("branch_call");
    public static final SoundEvent BRANCH_LOCK = register("branch_lock");
    public static final SoundEvent BRANCH_STRIKE = register("branch_strike");
    public static final SoundEvent REDLINE_CALL = register("redline_call");
    public static final SoundEvent REDLINE_LOCK = register("redline_lock");
    public static final SoundEvent REDLINE_STRIKE = register("redline_strike");
    public static final SoundEvent REDLINE_SHELTER = register("redline_shelter");
    public static final SoundEvent CASINO_CARD_SHUFFLE = register("casino_card_shuffle");
    public static final SoundEvent CASINO_CARD_PLACE = register("casino_card_place");
    public static final SoundEvent CASINO_CHIP_LAY = register("casino_chip_lay");
    public static final SoundEvent CASINO_CHIPS_STACK = register("casino_chips_stack");
    public static final SoundEvent CASINO_WHEEL_TICK = register("casino_wheel_tick");
    public static final SoundEvent CASINO_REELS_SPIN = register("casino_reels_spin");
    public static final SoundEvent CASINO_RPS_THROW = register("casino_rps_throw");
    public static final SoundEvent CASINO_TRIVIA_CHOICE = register("casino_trivia_choice");
    public static final SoundEvent CASINO_LOSS = register("casino_loss");
    public static final SoundEvent MAZE_SEAL = register("maze_seal");
    public static final SoundEvent MAZE_TRAP = register("maze_trap");
    public static final SoundEvent MAZE_RESET = register("maze_reset");

    private AdventureSounds() {
    }

    public static void init() {
        // Triggers static registration.
    }

    private static SoundEvent register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath("second_chance", path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}

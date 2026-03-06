package com.feel.gems.adventure.boss;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Sends combat tells to participants already selected by the encounter. */
final class BossAudio {
    private BossAudio() {
    }

    static void tell(List<ServerPlayer> players, SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE,
                    player.getX(), player.getY(), player.getZ(), volume, pitch,
                    player.getRandom().nextLong()));
        }
    }
}

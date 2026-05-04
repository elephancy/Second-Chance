package com.feel.gems.testutil;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class MinecraftBootstrap {
    private static final AtomicBoolean DONE = new AtomicBoolean(false);

    private MinecraftBootstrap() {
    }

    public static void ensure() {
        if (DONE.compareAndSet(false, true)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }
}

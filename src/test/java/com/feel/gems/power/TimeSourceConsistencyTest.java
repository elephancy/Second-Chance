package com.feel.gems.power;

import com.feel.gems.net.ServerAbilityNetworking;
import com.feel.gems.power.gem.spy.SpySystem;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.trade.GemTrading;
import com.feel.gems.trust.GemTrust;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class TimeSourceConsistencyTest {

    private static Path packageRoot(Class<?> anchor) {
        return Path.of("gems", "src", "main", "java").resolve(anchor.getPackageName().replace('.', '/'));
    }

    @Test
    void powerAndNetCodeDoesNotUsePerWorldTime() throws IOException {
        List<Path> roots = List.of(
                packageRoot(SpySystem.class),
            packageRoot(ServerAbilityNetworking.class),
            packageRoot(GemPlayerState.class),
            packageRoot(GemTrading.class),
            packageRoot(GemTrust.class)
        );

        List<String> offenders = new ArrayList<>();
        for (Path root : roots) {
            assertTrue(Files.isDirectory(root), "Core source directory is missing: " + root);
            try (var stream = Files.walk(root)) {
                stream.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        String src = Files.readString(p, StandardCharsets.UTF_8);
                        if (src.contains("getServerWorld().getTime()") || src.contains(".getTime()")) {
                            offenders.add(p.toString());
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }

        assertTrue(offenders.isEmpty(), "Found per-world time usage in: " + offenders);
    }
}


package com.feel.gems.item;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class GemItemGlintTest {

    @Test
    void usesEnchantmentGlintOverrideComponent() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "item", "GemItemGlint.java");
        assertTrue(Files.exists(src), "GemItemGlint.java is missing");

        String code = Files.readString(src);
        assertTrue(code.contains("DataComponents.ENCHANTMENT_GLINT_OVERRIDE"));
        assertTrue(code.contains("stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE"));
        assertTrue(code.contains("stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE"));
    }
}

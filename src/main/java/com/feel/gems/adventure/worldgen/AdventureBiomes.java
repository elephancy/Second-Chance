package com.feel.gems.adventure.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.api.TerraBlenderApi;
import terrablender.api.VanillaParameterOverlayBuilder;

import java.util.function.Consumer;

import static terrablender.api.ParameterUtils.*;

/** Rare Overworld pockets that carry a friend's architectural palette into the landscape. */
public final class AdventureBiomes implements TerraBlenderApi {
    public static final ResourceKey<Biome> HUSHWIRE_HEATH = key("hushwire_heath");
    public static final ResourceKey<Biome> INKBOUND_GROVE = key("inkbound_grove");
    public static final ResourceKey<Biome> RIVERWARD_BANKS = key("riverward_banks");
    public static final ResourceKey<Biome> SIGNAL_COAST = key("signal_coast");
    public static final ResourceKey<Biome> FORUM_GLADE = key("forum_glade");
    public static final ResourceKey<Biome> SURVEY_HIGHLANDS = key("survey_highlands");
    public static final ResourceKey<Biome> ARCHIVE_COMMONS = key("archive_commons");
    public static final ResourceKey<Biome> STORYBOOK_VALE = key("storybook_vale");
    public static final ResourceKey<Biome> LOST_WADI = key("lost_wadi");
    public static final ResourceKey<Biome> RECLAIMED_WOODLAND = key("reclaimed_woodland");
    public static final ResourceKey<Biome> SPLITSTONE_HEIGHTS = key("splitstone_heights");
    public static final ResourceKey<Biome> FRAMEFALL_RAVINE = key("framefall_ravine");
    public static final ResourceKey<Biome> LASTLIGHT_YARD = key("lastlight_yard");
    public static final ResourceKey<Biome> REHEARSAL_THICKET = key("rehearsal_thicket");
    public static final ResourceKey<Biome> BRANCH_TAIGA = key("branch_taiga");
    public static final ResourceKey<Biome> REDLINE_REFUGE = key("redline_refuge");
    public static final ResourceKey<Biome> UNWRITTEN_STAGE = key("unwritten_stage");
    public static final ResourceKey<Biome> DECO_QUARTER = key("deco_quarter");
    public static final ResourceKey<Biome> ESTATE_COMMONS = key("estate_commons");

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("gems", path));
    }

    @Override
    public void onTerraBlenderInitialized() {
        Regions.register(new DomainRegion("hushwire_heath", HUSHWIRE_HEATH,
                Temperature.span(Temperature.ICY, Temperature.NEUTRAL),
                Humidity.span(Humidity.DRY, Humidity.WET)));
        Regions.register(new DomainRegion("inkbound_grove", INKBOUND_GROVE,
                Temperature.span(Temperature.NEUTRAL, Temperature.WARM),
                Humidity.span(Humidity.WET, Humidity.HUMID)));
        Regions.register(new DomainRegion("riverward_banks", RIVERWARD_BANKS,
                Temperature.span(Temperature.NEUTRAL, Temperature.WARM),
                Humidity.span(Humidity.WET, Humidity.HUMID)));
        Regions.register(new DomainRegion("signal_coast", SIGNAL_COAST,
                Temperature.span(Temperature.NEUTRAL, Temperature.WARM),
                Humidity.span(Humidity.DRY, Humidity.HUMID), Continentalness.COAST));
        Regions.register(new DomainRegion("forum_glade", FORUM_GLADE,
                Temperature.span(Temperature.NEUTRAL, Temperature.WARM),
                Humidity.span(Humidity.WET, Humidity.HUMID),
                Continentalness.INLAND, Erosion.span(Erosion.EROSION_4, Erosion.EROSION_5),
                Weirdness.span(Weirdness.MID_SLICE_VARIANT_ASCENDING,
                        Weirdness.MID_SLICE_VARIANT_DESCENDING)));
        Regions.register(new DomainRegion("survey_highlands", SURVEY_HIGHLANDS,
                Temperature.span(Temperature.ICY, Temperature.NEUTRAL),
                Humidity.span(Humidity.DRY, Humidity.WET)));
        Regions.register(new DomainRegion("archive_commons", ARCHIVE_COMMONS,
                Temperature.span(Temperature.COOL, Temperature.WARM),
                Humidity.span(Humidity.NEUTRAL, Humidity.HUMID),
                Continentalness.INLAND, Erosion.span(Erosion.EROSION_2, Erosion.EROSION_4),
                Weirdness.span(Weirdness.MID_SLICE_NORMAL_ASCENDING,
                        Weirdness.MID_SLICE_NORMAL_DESCENDING)));
        Regions.register(new DomainRegion("storybook_vale", STORYBOOK_VALE,
                Temperature.span(Temperature.NEUTRAL, Temperature.WARM),
                Humidity.span(Humidity.DRY, Humidity.WET)));
        Regions.register(new DomainRegion("lost_wadi", LOST_WADI,
                Temperature.span(Temperature.WARM, Temperature.HOT),
                Humidity.span(Humidity.DRY, Humidity.DRY)));
        Regions.register(new DomainRegion("reclaimed_woodland", RECLAIMED_WOODLAND,
                Temperature.span(Temperature.NEUTRAL, Temperature.WARM),
                Humidity.span(Humidity.DRY, Humidity.WET)));
        Regions.register(new DomainRegion("splitstone_heights", SPLITSTONE_HEIGHTS,
                Temperature.span(Temperature.ICY, Temperature.NEUTRAL),
                Humidity.span(Humidity.DRY, Humidity.WET)));
        // These later districts use other erosion/weirdness bands so their
        // overlays do not replace the already playable friend regions above.
        Regions.register(new DomainRegion("framefall_ravine", FRAMEFALL_RAVINE,
                Temperature.span(Temperature.COOL, Temperature.NEUTRAL),
                Humidity.span(Humidity.DRY, Humidity.NEUTRAL),
                Continentalness.INLAND, Erosion.span(Erosion.EROSION_2, Erosion.EROSION_3),
                Weirdness.span(Weirdness.LOW_SLICE_NORMAL_DESCENDING,
                        Weirdness.LOW_SLICE_VARIANT_ASCENDING)));
        Regions.register(new DomainRegion("lastlight_yard", LASTLIGHT_YARD,
                Temperature.span(Temperature.WARM, Temperature.HOT),
                Humidity.span(Humidity.ARID, Humidity.DRY),
                Continentalness.INLAND, Erosion.span(Erosion.EROSION_3, Erosion.EROSION_4),
                Weirdness.MID_SLICE_VARIANT_ASCENDING.parameter()));
        Regions.register(new DomainRegion("rehearsal_thicket", REHEARSAL_THICKET,
                Temperature.span(Temperature.NEUTRAL, Temperature.HOT),
                Humidity.span(Humidity.NEUTRAL, Humidity.HUMID),
                Continentalness.INLAND, Erosion.span(Erosion.EROSION_4, Erosion.EROSION_5),
                Weirdness.HIGH_SLICE_VARIANT_DESCENDING.parameter()));
        Regions.register(new DomainRegion("branch_taiga", BRANCH_TAIGA,
                Temperature.span(Temperature.ICY, Temperature.NEUTRAL),
                Humidity.span(Humidity.NEUTRAL, Humidity.HUMID),
                Continentalness.INLAND, Erosion.span(Erosion.EROSION_2, Erosion.EROSION_3),
                Weirdness.MID_SLICE_VARIANT_DESCENDING.parameter()));
        Regions.register(new DomainRegion("redline_refuge", REDLINE_REFUGE,
                Temperature.span(Temperature.NEUTRAL, Temperature.WARM),
                Humidity.span(Humidity.ARID, Humidity.NEUTRAL),
                Continentalness.INLAND, Erosion.span(Erosion.EROSION_4, Erosion.EROSION_5),
                Weirdness.VALLEY.parameter()));
        Regions.register(new DomainRegion("unwritten_stage", UNWRITTEN_STAGE,
                Temperature.NEUTRAL.parameter(), Humidity.span(Humidity.WET, Humidity.HUMID),
                Continentalness.INLAND, Erosion.span(Erosion.EROSION_5, Erosion.EROSION_6),
                Weirdness.LOW_SLICE_VARIANT_ASCENDING.parameter()));
        Regions.register(new DomainRegion("deco_quarter", DECO_QUARTER,
                Temperature.span(Temperature.NEUTRAL, Temperature.HOT),
                Humidity.span(Humidity.ARID, Humidity.NEUTRAL),
                Continentalness.INLAND, Erosion.EROSION_6.parameter(),
                Weirdness.VALLEY.parameter()));
        // A broad, low-relief inland clearing is required across all 25
        // estate pieces and their 12-block arrivals. Structure generation
        // still rejects any candidate whose complete sample leaves it.
        Regions.register(new DomainRegion("estate_commons", ESTATE_COMMONS,
                Temperature.span(Temperature.COOL, Temperature.WARM),
                Humidity.span(Humidity.DRY, Humidity.HUMID),
                Continentalness.INLAND,
                Erosion.span(Erosion.EROSION_4, Erosion.EROSION_6),
                Weirdness.span(Weirdness.MID_SLICE_NORMAL_ASCENDING,
                        Weirdness.MID_SLICE_NORMAL_DESCENDING)));
    }

    private static final class DomainRegion extends Region {
        private final ResourceKey<Biome> biome;
        private final Climate.Parameter temperature;
        private final Climate.Parameter humidity;
        private final Continentalness continentalness;
        private final Climate.Parameter erosion;
        private final Climate.Parameter weirdness;

        private DomainRegion(String name, ResourceKey<Biome> biome,
                             Climate.Parameter temperature, Climate.Parameter humidity) {
            this(name, biome, temperature, humidity, Continentalness.INLAND);
        }

        private DomainRegion(String name, ResourceKey<Biome> biome,
                             Climate.Parameter temperature, Climate.Parameter humidity,
                             Continentalness continentalness) {
            this(name, biome, temperature, humidity, continentalness,
                    Erosion.span(Erosion.EROSION_0, Erosion.EROSION_1),
                    Weirdness.span(Weirdness.MID_SLICE_NORMAL_ASCENDING,
                            Weirdness.MID_SLICE_NORMAL_DESCENDING));
        }

        private DomainRegion(String name, ResourceKey<Biome> biome,
                             Climate.Parameter temperature, Climate.Parameter humidity,
                             Continentalness continentalness, Climate.Parameter erosion,
                             Climate.Parameter weirdness) {
            super(Identifier.fromNamespaceAndPath("gems", name), RegionType.OVERWORLD, 2);
            this.biome = biome;
            this.temperature = temperature;
            this.humidity = humidity;
            this.continentalness = continentalness;
            this.erosion = erosion;
            this.weirdness = weirdness;
        }

        @Override
        public void addBiomes(Registry<Biome> registry,
                              Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
            VanillaParameterOverlayBuilder overlay = new VanillaParameterOverlayBuilder();
            new ParameterPointListBuilder()
                    .temperature(temperature)
                    .humidity(humidity)
                    .continentalness(continentalness)
                    .erosion(erosion)
                    .depth(Depth.SURFACE)
                    .weirdness(weirdness)
                    .build().forEach(point -> overlay.add(point, biome));
            overlay.build().forEach(mapper);
        }
    }
}

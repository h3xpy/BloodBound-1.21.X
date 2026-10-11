package net.h3xpy.bloodbound.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * How long every perk and addon has spent equipped, across the whole server.
 * <p>
 * Counted in ticks of being in somebody's loadout, summed over everyone online, and saved with the
 * world — the point being to see which perks are worn all the time and which are never touched, so
 * a buff or a nerf can be aimed at something real.
 */
public class PerkUsageStats extends SavedData {

    /** The file this lives in, inside the world's data folder. */
    private static final String FILE = BloodBound.MODID + "_usage";
    /** How often the count is added to. Cheaper than every tick, and just as true over hours. */
    public static final int SAMPLE_INTERVAL = 20;

    private final Map<ResourceLocation, Long> perkTicks = new HashMap<>();
    private final Map<ResourceLocation, Long> addonTicks = new HashMap<>();

    public PerkUsageStats() {}

    public static PerkUsageStats get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PerkUsageStats::new, PerkUsageStats::load), FILE);
    }

    /** One sample: every perk and addon worn right now is credited with the interval. */
    public static void sample(MinecraftServer server, long gameTime) {
        if (gameTime % SAMPLE_INTERVAL != 0L) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }

        PerkUsageStats stats = get(server);
        for (ServerPlayer player : players) {
            PlayerPerkData data = PerkDataManager.get(player);
            for (int slot = 0; slot < PlayerPerkData.LOADOUT_SIZE; slot++) {
                ResourceLocation perkId = data.getLoadoutSlot(slot);
                if (perkId == null || data.getActiveTier(perkId) <= 0) {
                    continue;
                }
                stats.perkTicks.merge(perkId, (long) SAMPLE_INTERVAL, Long::sum);

                ResourceLocation addonId = data.equippedAddon(perkId);
                if (addonId != null) {
                    stats.addonTicks.merge(addonId, (long) SAMPLE_INTERVAL, Long::sum);
                }
            }
        }
        stats.setDirty();
    }

    /** One line of the report: what it is, and how long it has been worn. */
    public record Entry(ResourceLocation id, String name, long ticks) {}

    /** Every perk, longest worn first. Perks nobody has ever equipped are listed at zero. */
    public List<Entry> perks() {
        List<Entry> entries = new ArrayList<>();
        for (Perk perk : PerkRegistry.all()) {
            entries.add(new Entry(perk.id(), perk.displayName().getString(),
                    perkTicks.getOrDefault(perk.id(), 0L)));
        }
        entries.sort((a, b) -> Long.compare(b.ticks(), a.ticks()));
        return entries;
    }

    /** Every addon, longest worn first. */
    public List<Entry> addons() {
        List<Entry> entries = new ArrayList<>();
        for (Addon addon : AddonRegistry.all()) {
            entries.add(new Entry(addon.id(), addon.displayName().getString(),
                    addonTicks.getOrDefault(addon.id(), 0L)));
        }
        entries.sort((a, b) -> Long.compare(b.ticks(), a.ticks()));
        return entries;
    }

    /** A perk's popularity in this world, 1 to 5 stars. */
    public int perkStars(ResourceLocation perkId) {
        return stars(perkTicks, perkId);
    }

    /** An addon's popularity in this world, 1 to 5 stars. */
    public int addonStars(ResourceLocation addonId) {
        return stars(addonTicks, addonId);
    }

    /**
     * Stars by how long something has been worn next to the most worn of its kind: 5 from 60% of it,
     * 4 from 35%, 3 from 15%, 2 from 5%, and 1 below that or never worn at all.
     */
    private static int stars(Map<ResourceLocation, Long> ticks, ResourceLocation id) {
        long most = 0L;
        for (long value : ticks.values()) {
            most = Math.max(most, value);
        }
        long mine = ticks.getOrDefault(id, 0L);
        if (most <= 0L || mine <= 0L) {
            return 1;
        }
        double share = (double) mine / most;
        if (share >= 0.60D) {
            return 5;
        }
        if (share >= 0.35D) {
            return 4;
        }
        if (share >= 0.15D) {
            return 3;
        }
        return share >= 0.05D ? 2 : 1;
    }

    /** Wipes the record, for a fresh season or a fresh test. */
    public void reset() {
        perkTicks.clear();
        addonTicks.clear();
        setDirty();
    }

    // --- saving ---

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("perks", write(perkTicks));
        tag.put("addons", write(addonTicks));
        return tag;
    }

    private static CompoundTag write(Map<ResourceLocation, Long> source) {
        CompoundTag tag = new CompoundTag();
        source.forEach((id, ticks) -> tag.putLong(id.toString(), ticks));
        return tag;
    }

    public static PerkUsageStats load(CompoundTag tag, HolderLookup.Provider registries) {
        PerkUsageStats stats = new PerkUsageStats();
        read(tag.getCompound("perks"), stats.perkTicks);
        read(tag.getCompound("addons"), stats.addonTicks);
        return stats;
    }

    private static void read(CompoundTag tag, Map<ResourceLocation, Long> into) {
        for (String key : tag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                into.put(id, tag.getLong(key));
            }
        }
    }
}

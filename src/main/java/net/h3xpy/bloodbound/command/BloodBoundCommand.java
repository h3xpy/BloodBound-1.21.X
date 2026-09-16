package net.h3xpy.bloodbound.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.h3xpy.bloodbound.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /bloodbound} — operator tools for testing and for fixing up players' progress.
 * <p>
 * Every subcommand takes a player selector, applies the change, and pushes a fresh sync so the
 * target's screen and HUD update immediately.
 */
public final class BloodBoundCommand {

    /** Vanilla's "game master" level: ops and command blocks, not plain players. */
    private static final int PERMISSION_LEVEL = 2;

    private static final SuggestionProvider<CommandSourceStack> PERK_IDS = (context, builder) -> {
        List<ResourceLocation> ids = new ArrayList<>();
        PerkRegistry.all().forEach(perk -> ids.add(perk.id()));
        return SharedSuggestionProvider.suggestResource(ids, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> ADDON_IDS = (context, builder) -> {
        List<ResourceLocation> ids = new ArrayList<>();
        AddonRegistry.all().forEach(addon -> ids.add(addon.id()));
        return SharedSuggestionProvider.suggestResource(ids, builder);
    };

    private BloodBoundCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bloodbound")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(perkCommands())
                .then(addonCommands())
                .then(webCommands())
                .then(Commands.literal("shards")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                                        .executes(context -> giveShards(context,
                                                IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("cooldowns")
                        .then(Commands.literal("clear")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> apply(context, data -> data.clearCooldowns(),
                                                "bloodbound.command.cooldowns_cleared")))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> apply(context, PlayerPerkData::resetAll,
                                        "bloodbound.command.reset"))))
                .then(Commands.literal("info")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(BloodBoundCommand::info))));
    }

    // --- perks ---

    private static LiteralArgumentBuilder<CommandSourceStack> perkCommands() {
        return Commands.literal("perk")
                .then(Commands.literal("grant")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("perk", ResourceLocationArgument.id())
                                        .suggests(PERK_IDS)
                                        .executes(context -> grantPerk(context, Perk.MAX_TIER))
                                        .then(Commands.argument("tier",
                                                IntegerArgumentType.integer(1, Perk.MAX_TIER))
                                                .executes(context -> grantPerk(context,
                                                        IntegerArgumentType.getInteger(context, "tier")))))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("perk", ResourceLocationArgument.id())
                                        .suggests(PERK_IDS)
                                        .executes(BloodBoundCommand::revokePerk))))
                .then(Commands.literal("unlockall")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> apply(context, data -> {
                                    for (Perk perk : PerkRegistry.all()) {
                                        data.unlock(perk.id(), Perk.MAX_TIER);
                                    }
                                }, "bloodbound.command.perks_unlocked"))));
    }

    private static int grantPerk(CommandContext<CommandSourceStack> context, int tier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation perkId = ResourceLocationArgument.getId(context, "perk");
        Perk perk = PerkRegistry.get(perkId);
        if (perk == null) {
            context.getSource().sendFailure(
                    Component.translatable("bloodbound.command.unknown_perk", perkId.toString()));
            return 0;
        }
        // setPerkTier rather than unlock, so an admin can also dial a tier back down while testing.
        return apply(context, data -> data.setPerkTier(perkId, tier),
                "bloodbound.command.perk_granted", perk.displayName(), tier);
    }

    private static int revokePerk(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation perkId = ResourceLocationArgument.getId(context, "perk");
        Perk perk = PerkRegistry.get(perkId);
        if (perk == null) {
            context.getSource().sendFailure(
                    Component.translatable("bloodbound.command.unknown_perk", perkId.toString()));
            return 0;
        }
        return apply(context, data -> data.revokePerk(perkId),
                "bloodbound.command.perk_revoked", perk.displayName());
    }

    // --- addons ---

    private static LiteralArgumentBuilder<CommandSourceStack> addonCommands() {
        return Commands.literal("addon")
                .then(Commands.literal("grant")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("addon", ResourceLocationArgument.id())
                                        .suggests(ADDON_IDS)
                                        .executes(BloodBoundCommand::grantAddon))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("addon", ResourceLocationArgument.id())
                                        .suggests(ADDON_IDS)
                                        .executes(BloodBoundCommand::revokeAddon))))
                .then(Commands.literal("unlockall")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> apply(context, data -> {
                                    for (Addon addon : AddonRegistry.all()) {
                                        data.unlockAddon(addon.id());
                                    }
                                }, "bloodbound.command.addons_unlocked"))));
    }

    private static int grantAddon(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation addonId = ResourceLocationArgument.getId(context, "addon");
        Addon addon = AddonRegistry.get(addonId);
        if (addon == null) {
            context.getSource().sendFailure(
                    Component.translatable("bloodbound.command.unknown_addon", addonId.toString()));
            return 0;
        }
        return apply(context, data -> data.unlockAddon(addonId),
                "bloodbound.command.addon_granted", addon.displayName());
    }

    private static int revokeAddon(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation addonId = ResourceLocationArgument.getId(context, "addon");
        Addon addon = AddonRegistry.get(addonId);
        if (addon == null) {
            context.getSource().sendFailure(
                    Component.translatable("bloodbound.command.unknown_addon", addonId.toString()));
            return 0;
        }
        return apply(context, data -> data.revokeAddon(addonId),
                "bloodbound.command.addon_revoked", addon.displayName());
    }

    // --- soulweb ---

    private static LiteralArgumentBuilder<CommandSourceStack> webCommands() {
        return Commands.literal("web")
                .then(Commands.literal("reroll")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> applyToPlayers(context, player -> {
                                    PlayerPerkData data = PerkDataManager.get(player);
                                    data.rerollSoulweb(player.getRandom(), player.registryAccess());
                                }, "bloodbound.command.web_rerolled"))))
                .then(Commands.literal("level")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 999))
                                        .executes(context -> {
                                            int level = IntegerArgumentType.getInteger(context, "level");
                                            return applyToPlayers(context, player -> {
                                                PlayerPerkData data = PerkDataManager.get(player);
                                                data.setWebLevel(level);
                                                data.getOrCreateSoulweb(player.getRandom(),
                                                        player.registryAccess());
                                            }, "bloodbound.command.web_level", level);
                                        }))));
    }

    // --- shards ---

    private static int giveShards(CommandContext<CommandSourceStack> context, int amount)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return applyToPlayers(context, player -> {
            int remaining = amount;
            while (remaining > 0) {
                int size = Math.min(remaining, ModItems.SOUL_SHARD.get().getDefaultMaxStackSize());
                ItemStack stack = new ItemStack(ModItems.SOUL_SHARD.get(), size);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                remaining -= size;
            }
        }, "bloodbound.command.shards_given", amount);
    }

    // --- info ---

    private static int info(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        PlayerPerkData data = PerkDataManager.get(target);
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.translatable("bloodbound.command.info_header", target.getName())
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("bloodbound.command.info_web",
                data.webLevel(), PerkDataManager.countShards(target)), false);

        for (Perk perk : PerkRegistry.all()) {
            int tier = data.getUnlockedTier(perk.id());
            if (tier <= 0) {
                continue;
            }
            boolean equipped = data.isEquipped(perk.id());
            Addon fitted = AddonRegistry.get(data.equippedAddon(perk.id()));
            Component addonName = fitted != null ? fitted.displayName() : Component.literal("-");
            source.sendSuccess(() -> Component.translatable("bloodbound.command.info_perk",
                    perk.displayName(), tier, equipped ? "*" : " ", addonName)
                    .withStyle(equipped ? ChatFormatting.WHITE : ChatFormatting.GRAY), false);
        }

        int addonCount = data.unlockedAddons().size();
        source.sendSuccess(() -> Component.translatable("bloodbound.command.info_addons", addonCount), false);
        return 1;
    }

    // --- plumbing ---

    /** Applies a change to each selected player's data, then syncs and reports. */
    private static int apply(CommandContext<CommandSourceStack> context, Consumer<PlayerPerkData> change,
            String messageKey, Object... args) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return applyToPlayers(context, player -> change.accept(PerkDataManager.get(player)), messageKey, args);
    }

    private static int applyToPlayers(CommandContext<CommandSourceStack> context, Consumer<ServerPlayer> change,
            String messageKey, Object... args) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            change.accept(player);
            PerkDataManager.sync(player);
        }

        Object[] messageArgs = new Object[args.length + 1];
        messageArgs[0] = targets.size();
        System.arraycopy(args, 0, messageArgs, 1, args.length);

        context.getSource().sendSuccess(() -> Component.translatable(messageKey, messageArgs), true);
        return targets.size();
    }
}

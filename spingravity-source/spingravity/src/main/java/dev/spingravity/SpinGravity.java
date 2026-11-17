package dev.spingravity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SpinGravity implements ModInitializer {
    /** Vanilla gravity (blocks per tick^2). In this mod that is "1 G" of gameplay gravity. */
    private static final double VANILLA_GRAVITY = 0.08;
    /** The gravity attribute is capped at 1.0 by the game. */
    private static final double MAX_GRAVITY_ATTRIBUTE = 1.0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static SpinSettings settings = new SpinSettings();
    private static final Set<UUID> HUD = new HashSet<>();
    private static int hudTimer = 0;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(SpinGravity::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(SpinGravity::save);
        ServerTickEvents.END_SERVER_TICK.register(SpinGravity::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(buildCommand()));
    }

    // ------------------------------------------------------------------ gravity

    private static void tick(MinecraftServer server) {
        boolean showHud = ++hudTimer >= 10;
        if (showHud) hudTimer = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyGravity(player);
            if (showHud && HUD.contains(player.getUUID())) {
                double g = settings.gAt(player.getY());
                player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                        "%.2f G   (sea level %.2f G, %.1f rpm)", g, settings.floorG(), settings.rpm)), true);
            }
        }
    }

    private static void applyGravity(ServerPlayer player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;

        double target = VANILLA_GRAVITY;
        if (settings.enabled && player.level().dimension().equals(Level.OVERWORLD)) {
            target = VANILLA_GRAVITY * settings.gAt(player.getY());
        }
        target = Math.min(target, MAX_GRAVITY_ATTRIBUTE);
        target = Math.round(target * 10000.0) / 10000.0; // avoid resyncing on tiny changes

        if (gravity.getBaseValue() != target) {
            gravity.setBaseValue(target);
        }
    }

    // ------------------------------------------------------------------ persistence (per world)

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("spingravity.json");
    }

    private static void load(MinecraftServer server) {
        Path file = file(server);
        settings = new SpinSettings();
        if (Files.exists(file)) {
            try {
                SpinSettings loaded = GSON.fromJson(Files.readString(file), SpinSettings.class);
                if (loaded != null) settings = loaded;
            } catch (IOException | RuntimeException e) {
                System.err.println("[spingravity] could not read " + file + ": " + e);
            }
        }
    }

    private static void save(MinecraftServer server) {
        try {
            Files.writeString(file(server), GSON.toJson(settings));
        } catch (IOException e) {
            System.err.println("[spingravity] could not write settings: " + e);
        }
    }

    // ------------------------------------------------------------------ commands

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("spin")
                .executes(ctx -> info(ctx))
                .then(Commands.literal("info").executes(ctx -> info(ctx)))
                .then(Commands.literal("on").executes(ctx -> {
                    settings.enabled = true;
                    return changed(ctx);
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    settings.enabled = false;
                    return changed(ctx);
                }))
                .then(Commands.literal("rpm")
                        .then(Commands.argument("rpm", DoubleArgumentType.doubleArg(0.0, 200.0))
                                .executes(ctx -> {
                                    settings.rpm = DoubleArgumentType.getDouble(ctx, "rpm");
                                    return changed(ctx);
                                })))
                .then(Commands.literal("radius")
                        .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(8.0, 100000.0))
                                .executes(ctx -> {
                                    // keep the same sea-level G when the radius changes
                                    double g = settings.floorG();
                                    settings.radius = DoubleArgumentType.getDouble(ctx, "blocks");
                                    settings.setFloorG(g);
                                    return changed(ctx);
                                })))
                .then(Commands.literal("gravity")
                        .then(Commands.argument("g", DoubleArgumentType.doubleArg(0.0, 12.5))
                                .executes(ctx -> {
                                    settings.setFloorG(DoubleArgumentType.getDouble(ctx, "g"));
                                    return changed(ctx);
                                })))
                .then(Commands.literal("floor")
                        .then(Commands.literal("here").executes(ctx -> {
                            settings.floorY = ctx.getSource().getPosition().y;
                            return changed(ctx);
                        }))
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg(-2048.0, 2048.0))
                                .executes(ctx -> {
                                    settings.floorY = DoubleArgumentType.getDouble(ctx, "y");
                                    return changed(ctx);
                                })))
                .then(Commands.literal("preset")
                        .then(Commands.literal("cooper").executes(ctx -> {
                            // big habitat: 1 km radius, 1 G at sea level
                            settings.radius = 1000.0;
                            settings.floorY = 63.0;
                            settings.setFloorG(1.0);
                            settings.enabled = true;
                            return changed(ctx);
                        }))
                        .then(Commands.literal("tube").executes(ctx -> {
                            // tight tube: 128 block radius, 1 G at your feet right now
                            settings.radius = 128.0;
                            settings.floorY = ctx.getSource().getPosition().y;
                            settings.setFloorG(1.0);
                            settings.enabled = true;
                            return changed(ctx);
                        })))
                .then(Commands.literal("hud").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    boolean now = HUD.add(player.getUUID());
                    if (!now) HUD.remove(player.getUUID());
                    reply(ctx, "Gravity readout " + (now ? "on" : "off"));
                    return 1;
                }));
    }

    private static int changed(CommandContext<CommandSourceStack> ctx) {
        save(ctx.getSource().getServer());
        return info(ctx);
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        SpinSettings s = settings;
        double y = ctx.getSource().getPosition().y;
        reply(ctx, String.format(Locale.ROOT, "Spin gravity: %s (overworld only)", s.enabled ? "ON" : "OFF"));
        reply(ctx, String.format(Locale.ROOT, "Spin: %.3f rpm  (one turn every %.1f s, floor moves at %.1f m/s)",
                s.rpm, s.periodSeconds(), s.rimSpeed()));
        reply(ctx, String.format(Locale.ROOT, "Radius: %.0f blocks to the axis at Y=%.0f", s.radius, s.axisY()));
        reply(ctx, String.format(Locale.ROOT, "Gravity at sea level (Y=%.0f): %.3f G = %.2f m/s^2",
                s.floorY, s.floorG(), s.floorG() * SpinSettings.STANDARD_G));
        reply(ctx, String.format(Locale.ROOT, "Gravity where you are (Y=%.0f): %.3f G", y, s.gAt(y)));
        reply(ctx, String.format(Locale.ROOT, "Spin needed for 1 G at this radius: %.3f rpm",
                SpinSettings.rpmForFloorG(s.radius, 1.0)));
        return 1;
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }
}

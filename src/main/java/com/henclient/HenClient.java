package com.henclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class HenClient implements ClientModInitializer {
    public static final int RED = 0xFFE53935;
    public static final int DARK = 0xFF151515;
    public static final int PANEL = 0xFF222222;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int MUTED = 0xFFAAAAAA;

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Path CONFIG = FabricLoader.getInstance()
            .getConfigDir().resolve("henclient.properties");

    private static final List<Module> MODULES = new ArrayList<>();
    private static final Set<Integer> HELD = new HashSet<>();
    private static final List<String> STORAGE_RESULTS = new ArrayList<>();
    private static final List<net.minecraft.core.BlockPos> STORAGE_POSITIONS = new ArrayList<>();
    private static final List<String> SPAWNER_RESULTS = new ArrayList<>();
    private static final List<String> SUS_CHUNK_RESULTS = new ArrayList<>();
    private static int scanTicks = 0;
    private static ArmorStand freecamEntity;
    private static int attackTicks = 0;
    private static int espRenderTicks = 0;

    private static int guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    private static final class Module {
        final String id;
        final String name;
        int key;
        boolean enabled;

        Module(String id, String name, int key, boolean enabled) {
            this.id = id;
            this.name = name;
            this.key = key;
            this.enabled = enabled;
        }
    }

    private static void addModule(
            String id, String name, int key, boolean enabled) {
        MODULES.add(new Module(id, name, key, enabled));
    }

    @Override
    public void onInitializeClient() {
        addModule("coords", "Coordinates", GLFW.GLFW_KEY_C, true);
        addModule("fps", "FPS Counter", GLFW.GLFW_KEY_F, true);
        addModule("health", "Health HUD", GLFW.GLFW_KEY_H, false);
        addModule("sprint", "Sprint Status", GLFW.GLFW_KEY_G, false);
        addModule("armor", "Armor Status", GLFW.GLFW_KEY_J, true);
        addModule("direction", "Direction", GLFW.GLFW_KEY_N, true);
        addModule("biome", "Biome", GLFW.GLFW_KEY_B, false);
        addModule("storage", "Storage Finder", GLFW.GLFW_KEY_V, false);
        addModule("spawners", "Spawner Finder", GLFW.GLFW_KEY_P, false);
        addModule("suschunk", "Sus Chunk Finder", GLFW.GLFW_KEY_U, false);
        addModule("freecam", "Freecam", GLFW.GLFW_KEY_X, false);
        addModule("storageesp", "Storage ESP", GLFW.GLFW_KEY_Z, false);
        addModule("autoclicker", "Auto Clicker", GLFW.GLFW_KEY_K, false);
        addModule("aimassist", "Aim Assist", GLFW.GLFW_KEY_L, false);

        loadConfig();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                return;
            }

            if (++scanTicks >= 20) {
                scanTicks = 0;
                scanNearbyBlocks();
            }

            tickFreecam(client);
            tickCombat(client);

            // Avoid toggling modules while a screen is open.
            if (client.screen != null) {
                HELD.clear();
                return;
            }

            if (pressed(guiKey)) {
                client.setScreen(new HenScreen());
                return;
            }

            for (Module module : MODULES) {
                if (pressed(module.key)) {
                    module.enabled = !module.enabled;
                    saveConfig();
                }
            }
        });

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> renderStorageEsp(context));

        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            if (MC.player == null || MC.level == null) {
                return;
            }

            int x = 8;
            int y = 8;

            graphics.fill(x - 4, y - 4, x + 142, y + 16, DARK);
            graphics.fill(x - 4, y - 4, x - 1, y + 16, RED);
            graphics.drawString(MC.font, "HEN CLIENT", x + 3, y, RED);

            y += 23;

            if (enabled("fps")) {
                drawHud(graphics, "FPS: " + MC.getFps(), x, y);
                y += 13;
            }

            if (enabled("coords")) {
                var p = MC.player;
                String coords = String.format(
                        "XYZ: %d, %d, %d",
                        (int) Math.floor(p.getX()),
                        (int) Math.floor(p.getY()),
                        (int) Math.floor(p.getZ())
                );
                drawHud(graphics, coords, x, y);
                y += 13;
            }

            if (enabled("health")) {
                drawHud(graphics,
                        String.format("Health: %.1f", MC.player.getHealth()),
                        x, y);
                y += 13;
            }

            if (enabled("sprint")) {
                drawHud(graphics,
                        "Sprinting: " + (MC.player.isSprinting() ? "ON" : "OFF"),
                        x, y);
                y += 13;
            }

            if (enabled("armor")) {
                drawHud(graphics, armorText(), x, y);
                y += 13;
            }

            if (enabled("direction")) {
                drawHud(graphics, directionText(), x, y);
                y += 13;
            }

            if (enabled("biome")) {
                drawHud(graphics, biomeText(), x, y);
                y += 13;
            }

            if (enabled("freecam")) {
                drawHud(graphics, "FREECAM ACTIVE", x, y);
                y += 13;
            }

            if (enabled("storageesp")) {
                drawHud(graphics, "Storage ESP: " + STORAGE_RESULTS.size() + " blocks", x, y);
                y += 13;
            }

            if (enabled("autoclicker")) {
                drawHud(graphics, "Auto Clicker: ON", x, y);
                y += 13;
            }

            if (enabled("aimassist")) {
                drawHud(graphics, "Aim Assist: ON", x, y);
                y += 13;
            }

            if (enabled("storage")) {
                drawHud(graphics, "Storage nearby: " + STORAGE_RESULTS.size(), x, y);
                y += 13;
                for (int i = 0; i < Math.min(3, STORAGE_RESULTS.size()); i++) {
                    drawHud(graphics, "  " + STORAGE_RESULTS.get(i), x, y);
                    y += 13;
                }
            }

            if (enabled("spawners")) {
                drawHud(graphics, "Spawners nearby: " + SPAWNER_RESULTS.size(), x, y);
                y += 13;
                for (int i = 0; i < Math.min(3, SPAWNER_RESULTS.size()); i++) {
                    drawHud(graphics, "  " + SPAWNER_RESULTS.get(i), x, y);
                    y += 13;
                }
            }

            if (enabled("suschunk")) {
                drawHud(graphics, "Sus chunks: " + SUS_CHUNK_RESULTS.size(), x, y);
                y += 13;
                for (int i = 0; i < Math.min(2, SUS_CHUNK_RESULTS.size()); i++) {
                    drawHud(graphics, "  " + SUS_CHUNK_RESULTS.get(i), x, y);
                    y += 13;
                }
            }
        });
    }

    private static void drawHud(
            GuiGraphics graphics, String text, int x, int y) {
        int width = MC.font.width(text);
        graphics.fill(x - 2, y - 2, x + width + 5, y + 11, DARK);
        graphics.drawString(MC.font, text, x, y, WHITE);
    }

    /** Scans already-loaded blocks only. */
    private static void scanNearbyBlocks() {
        if (MC.player == null || MC.level == null) return;

        STORAGE_RESULTS.clear();
        STORAGE_POSITIONS.clear();
        SPAWNER_RESULTS.clear();
        SUS_CHUNK_RESULTS.clear();

        var playerPos = MC.player.blockPosition();
        int radius = 12;
        int minY = MC.level.getMinBuildHeight();
        int maxY = MC.level.getMaxBuildHeight() - 1;

        java.util.Map<Long, Integer> interestingByChunk = new java.util.HashMap<>();

        for (int x = playerPos.getX() - radius; x <= playerPos.getX() + radius; x++) {
            for (int z = playerPos.getZ() - radius; z <= playerPos.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    var pos = new net.minecraft.core.BlockPos(x, y, z);
                    if (!MC.level.hasChunkAt(pos)) continue;

                    var state = MC.level.getBlockState(pos);
                    var block = state.getBlock();
                    String found = null;

                    if (block instanceof net.minecraft.world.level.block.ChestBlock
                            || block instanceof net.minecraft.world.level.block.BarrelBlock
                            || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
                        found = "Storage " + x + " " + y + " " + z;
                        if (STORAGE_RESULTS.size() < 80) {
                            STORAGE_RESULTS.add(found);
                            STORAGE_POSITIONS.add(pos.immutable());
                        }
                    }

                    if (block instanceof net.minecraft.world.level.block.SpawnerBlock) {
                        found = "Spawner " + x + " " + y + " " + z;
                        if (SPAWNER_RESULTS.size() < 50) SPAWNER_RESULTS.add(found);
                    }

                    boolean belowStoneLine = y >= MC.level.getMinBuildHeight() && y <= 64;
                    boolean unnatural = belowStoneLine && (
                            block == net.minecraft.world.level.block.Blocks.CHEST
                            || block == net.minecraft.world.level.block.Blocks.BARREL
                            || block == net.minecraft.world.level.block.Blocks.SPAWNER
                            || block == net.minecraft.world.level.block.Blocks.OBSIDIAN
                            || block == net.minecraft.world.level.block.Blocks.COBBLESTONE
                            || block == net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE
                            || block == net.minecraft.world.level.block.Blocks.OAK_PLANKS
                            || block == net.minecraft.world.level.block.Blocks.TORCH
                            || block == net.minecraft.world.level.block.Blocks.RAIL);
                    if (unnatural) {
                        int chunkX = x >> 4;
                        int chunkZ = z >> 4;
                        long chunkKey = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                        interestingByChunk.merge(chunkKey, 1, Integer::sum);
                    }
                }
            }
        }

        // Heuristic only: multiple discovered storage/spawner blocks in a chunk
        // are candidates. The scan is a heuristic, not proof of player activity.
        for (var entry : interestingByChunk.entrySet()) {
            if (entry.getValue() >= 4 && SUS_CHUNK_RESULTS.size() < 20) {
                int chunkX = (int) (entry.getKey() >> 32);
                int chunkZ = (int) (long) entry.getKey();
                SUS_CHUNK_RESULTS.add("Chunk " + chunkX + ", " + chunkZ
                        + " (" + entry.getValue() + " finds)");
            }
        }
    }

    private static void tickFreecam(net.minecraft.client.Minecraft client) {
        if (client.player == null || client.level == null) return;
        if (!enabled("freecam")) {
            if (freecamEntity != null) {
                client.setCameraEntity(client.player);
                freecamEntity.discard();
                freecamEntity = null;
            }
            return;
        }
        if (freecamEntity == null || freecamEntity.isRemoved()) {
            freecamEntity = EntityType.ARMOR_STAND.create(client.level, EntitySpawnReason.COMMAND);
            if (freecamEntity == null) return;
            freecamEntity.setInvisible(true);
            freecamEntity.setNoGravity(true);
            freecamEntity.setInvulnerable(true);
            freecamEntity.setSilent(true);
            freecamEntity.setPos(client.player.getX(), client.player.getEyeY() - freecamEntity.getEyeHeight(), client.player.getZ());
            freecamEntity.setYRot(client.player.getYRot());
            freecamEntity.setXRot(client.player.getXRot());
            freecamEntity.setId(987654);
            client.level.addEntity(freecamEntity);
            client.setCameraEntity(freecamEntity);
        }
        float yaw = freecamEntity.getYRot();
        double speed = client.options.keySprint.isDown() ? 0.85 : 0.35;
        double forward = (client.options.keyUp.isDown() ? 1 : 0) - (client.options.keyDown.isDown() ? 1 : 0);
        double side = (client.options.keyRight.isDown() ? 1 : 0) - (client.options.keyLeft.isDown() ? 1 : 0);
        double vertical = (client.options.keyJump.isDown() ? 1 : 0) - (client.options.keyShift.isDown() ? 1 : 0);
        double rad = Math.toRadians(yaw);
        double dx = (-Math.sin(rad) * forward + Math.cos(rad) * side) * speed;
        double dz = (Math.cos(rad) * forward + Math.sin(rad) * side) * speed;
        freecamEntity.setPos(freecamEntity.getX() + dx, freecamEntity.getY() + vertical * speed, freecamEntity.getZ() + dz);
        // The active camera entity receives mouse-look input independently of the player.
    }

    private static void tickCombat(net.minecraft.client.Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null) return;
        if (enabled("aimassist")) {
            LivingEntity target = null;
            double best = 6.0;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living) || entity == client.player || !living.isAlive()) continue;
                double distance = client.player.distanceTo(living);
                if (distance > 6.0 || !client.player.hasLineOfSight(living)) continue;
                double yawTo = Math.toDegrees(Math.atan2(living.getZ() - client.player.getZ(), living.getX() - client.player.getX())) - 90.0;
                double diff = Math.abs(net.minecraft.util.Mth.wrapDegrees((float) yawTo - client.player.getYRot()));
                if (diff < 24.0 && distance < best) { best = distance; target = living; }
            }
            if (target != null) {
                double dx = target.getX() - client.player.getX();
                double dz = target.getZ() - client.player.getZ();
                double dy = target.getEyeY() - client.player.getEyeY();
                float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float targetPitch = (float)-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                client.player.setYRot(client.player.getYRot() + net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(targetYaw - client.player.getYRot()), -3.0f, 3.0f));
                client.player.setXRot(client.player.getXRot() + net.minecraft.util.Mth.clamp(targetPitch - client.player.getXRot(), -2.0f, 2.0f));
            }
        }
        if (enabled("autoclicker") && ++attackTicks >= 4) {
            attackTicks = 0;
            if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() instanceof LivingEntity living && living.isAlive() && client.player.distanceTo(living) <= 4.5) {
                client.startAttack();
            }
        }
    }

    private static void renderStorageEsp(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        if (!enabled("storageesp") || MC.level == null || MC.player == null || STORAGE_RESULTS.isEmpty()) return;
        Vec3 camera = MC.gameRenderer.getMainCamera().getPosition();
        MultiBufferSource consumers = context.consumers();
        VertexConsumer lines = consumers.getBuffer(RenderType.lines());
        var pose = context.matrices();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        int count = 0;
        for (net.minecraft.core.BlockPos pos : STORAGE_POSITIONS) {
            if (count >= 80) break;
            if (pos.distToCenterSqr(MC.player.position()) > 32.0 * 32.0 || !MC.level.hasChunkAt(pos)) continue;
            var block = MC.level.getBlockState(pos).getBlock();
            if (block instanceof net.minecraft.world.level.block.ChestBlock || block instanceof net.minecraft.world.level.block.BarrelBlock || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
                AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
                LevelRenderer.renderLineBox(pose, lines, box, 1.0f, 0.22f, 0.36f, 1.0f);
                count++;
            }
        }
        if (consumers instanceof MultiBufferSource.BufferSource source) source.endBatch(RenderType.lines());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        pose.popPose();
    }

    private static String armorText() {
        var player = MC.player;
        int equipped = 0;
        int damaged = 0;

        for (var stack : player.getInventory().armor) {
            if (!stack.isEmpty()) {
                equipped++;
                if (stack.isDamageableItem()) {
                    int remaining = stack.getMaxDamage() - stack.getDamageValue();
                    if (remaining <= Math.max(1, stack.getMaxDamage() / 10)) {
                        damaged++;
                    }
                }
            }
        }

        return "Armor: " + equipped + "/4" + (damaged > 0 ? " (" + damaged + " low)" : "");
    }

    private static String directionText() {
        float yaw = MC.player.getYRot();
        String[] directions = {"South", "South-West", "West", "North-West",
                "North", "North-East", "East", "South-East"};
        int index = Math.floorMod(Math.round(yaw / 45.0f), 8);
        return "Facing: " + directions[index];
    }

    private static String biomeText() {
        var biome = MC.level.getBiome(MC.player.blockPosition());
        return "Biome: " + biome.unwrapKey()
                .map(key -> key.location().getPath().replace('_', ' '))
                .orElse("Unknown");
    }

    private static boolean enabled(String id) {
        return MODULES.stream()
                .anyMatch(m -> m.id.equals(id) && m.enabled);
    }

    private static boolean pressed(int key) {
        long window = MC.getWindow().getWindow();
        boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;

        if (!down) {
            HELD.remove(key);
            return false;
        }

        return HELD.add(key);
    }

    private static String keyName(int key) {
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) {
            return name.toUpperCase();
        }

        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "RSHIFT";
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT) return "LSHIFT";
        if (key == GLFW.GLFW_KEY_SPACE) return "SPACE";
        if (key == GLFW.GLFW_KEY_ESCAPE) return "ESC";

        return "KEY " + key;
    }

    private static void loadConfig() {
        Properties properties = new Properties();

        if (Files.exists(CONFIG)) {
            try (InputStream in = Files.newInputStream(CONFIG)) {
                properties.load(in);
            } catch (IOException e) {
                System.err.println("Hen Client: config load failed: " + e);
            }
        }

        guiKey = parseKey(properties, "key.gui", guiKey);

        for (Module module : MODULES) {
            module.key = parseKey(
                    properties, "key." + module.id, module.key);
            module.enabled = Boolean.parseBoolean(
                    properties.getProperty(
                            "enabled." + module.id,
                            Boolean.toString(module.enabled)));
        }
    }

    private static int parseKey(
            Properties properties, String name, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(
                    name, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("key.gui", Integer.toString(guiKey));

        for (Module module : MODULES) {
            properties.setProperty(
                    "key." + module.id, Integer.toString(module.key));
            properties.setProperty(
                    "enabled." + module.id,
                    Boolean.toString(module.enabled));
        }

        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                properties.store(out, "Hen Client settings");
            }
        } catch (IOException e) {
            System.err.println("Hen Client: config save failed: " + e);
        }
    }

    private static final class HenScreen extends Screen {
        private static final int WIDTH = 360;
        private static final int HEIGHT = 590;

        private String bindTarget = null;

        HenScreen() {
            super(Component.literal("Hen Client"));
        }

        @Override
        public void render(
                GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0x99000000);

            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;

            g.fill(left, top, left + WIDTH, top + HEIGHT, DARK);
            g.fill(left, top, left + WIDTH, top + 3, RED);
            g.fill(left, top + 3, left + WIDTH, top + 37, PANEL);

            g.drawString(font, "HEN CLIENT", left + 14, top + 13, RED);
            g.drawString(font, "FABRIC 1.21.11",
                    left + 235, top + 13, MUTED);

            g.drawString(font, "MODULES", left + 14, top + 48, WHITE);

            String guiBind = bindTarget != null
                    && bindTarget.equals("gui")
                    ? "Press a key..." : keyName(guiKey);

            g.fill(left + 206, top + 43, left + 345, top + 62,
                    bindTarget != null && bindTarget.equals("gui")
                            ? RED : PANEL);
            g.drawString(font, "GUI KEY: " + guiBind,
                    left + 211, top + 49, WHITE);

            int rowY = top + 72;

            for (Module module : MODULES) {
                g.fill(left + 10, rowY, left + WIDTH - 10,
                        rowY + 29, PANEL);

                g.fill(left + 16, rowY + 7, left + 25,
                        rowY + 16, module.enabled ? RED : 0xFF555555);

                g.drawString(font, module.name,
                        left + 33, rowY + 10, WHITE);

                boolean binding = module.id.equals(bindTarget);
                String keyText = binding
                        ? "PRESS KEY" : keyName(module.key);

                g.fill(left + 245, rowY + 4, left + 340,
                        rowY + 25, binding ? RED : DARK);

                int textWidth = font.width(keyText);
                g.drawString(font, keyText,
                        left + 292 - textWidth / 2,
                        rowY + 10, WHITE);

                rowY += 34;
            }

            String footer = bindTarget == null
                    ? "Click a module to toggle it | Click its key to rebind"
                    : "Press a key to bind | ESC cancels";

            g.drawString(font, footer,
                    left + 12, top + HEIGHT - 18, MUTED);

            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(
                double mouseX, double mouseY, int button) {
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;

            if (button == 0) {
                if (mouseX >= left + 206 && mouseX <= left + 345
                        && mouseY >= top + 43 && mouseY <= top + 62) {
                    bindTarget = "gui";
                    return true;
                }

                int rowY = top + 72;

                for (Module module : MODULES) {
                    if (mouseY >= rowY && mouseY < rowY + 29
                            && mouseX >= left + 10
                            && mouseX <= left + WIDTH - 10) {
                        if (mouseX >= left + 240) {
                            bindTarget = module.id;
                        } else {
                            module.enabled = !module.enabled;
                            saveConfig();
                        }
                        return true;
                    }
                    rowY += 34;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(
                int keyCode, int scanCode, int modifiers) {
            if (bindTarget != null) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    bindTarget = null;
                    return true;
                }

                if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                    if (bindTarget.equals("gui")) {
                        guiKey = keyCode;
                    } else {
                        for (Module module : MODULES) {
                            if (module.id.equals(bindTarget)) {
                                module.key = keyCode;
                                break;
                            }
                        }
                    }

                    bindTarget = null;
                    saveConfig();
                }
                return true;
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
# HenClient Reborn

A client-side Fabric mod project for **Minecraft Java 1.21.11** and **Java 21**.

## Features

- **Freecam** (`X`): moves a detached camera using WASD, Space, and Shift; hold Sprint for faster movement. Your player stays behind.
- **Storage ESP** (`Z`): red outlines for nearby chests, barrels, and shulker boxes, including outlines rendered without depth testing.
- **Storage Finder** (`V`): lists nearby storage positions in the HUD.
- **Spawner Finder** (`P`): lists detected spawner positions in the scan region.
- **Sus Chunk Finder** (`U`): heuristic scan for clusters of non-natural blocks in the stone layer (chests, spawners, obsidian, cobblestone, planks, torches, rails). It marks candidates, not proof of anything.
- **Auto Clicker** (`K`): attacks the currently targeted living entity at a modest fixed interval while enabled.
- **Aim Assist** (`L`): subtle rotation assistance toward a nearby visible living entity in a narrow yaw cone.
- HUD modules: Coordinates (`C`), FPS (`F`), Health (`H`), Sprint (`G`), Armor (`J`), Direction (`N`), Biome (`B`).
- **Right Shift** opens the module menu. Click a module name to toggle it; click its key field to rebind it. Settings are saved to `config/henclient.properties`.

## Build

Requirements: JDK 21 and Gradle 8.14.3 or compatible.

```bash
gradle --no-daemon build
```

The jar will be created in `build/libs/`. GitHub Actions also builds the jar and uploads it as an artifact for each push or pull request.

## Install

1. Install Fabric Loader for Minecraft 1.21.11.
2. Put a matching Fabric API jar in your `mods` folder.
3. Put the built `henclient-1.0.0.jar` in the same `mods` folder.
4. Launch the Fabric 1.21.11 profile.

## Notes

This is a new implementation and has not been compiled or launched in this environment because Gradle dependencies cannot be downloaded here. The GitHub Actions workflow is included to perform a real dependency-resolved build. Review server rules before using combat automation or ESP on multiplayer servers.

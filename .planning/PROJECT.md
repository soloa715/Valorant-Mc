# Project Overview: ValorantMC

## 🎯 Vision & Purpose
`ValorantMC` brings a full-featured competitive Valorant experience directly into Minecraft, featuring 17 distinct agents, weapons with authentic spray and recoil mechanics, a round-based economy, custom maps, and spike plant/defuse gameplay.

## 🏗️ Architecture
- **Engine**: Paper 1.21.11 Server Plugin + Fabric 0.19.2 Companion Client Mod.
- **Language**: Java 21 / 25.
- **Build System**: Maven (`pom.xml`) + Gradle (`mcreator.gradle`).
- **Assets**: Custom 3D weapon models delivered via resource pack using `CustomModelData`.

## 📦 Key Subsystems
1. **Agent Engine**: Implementation of 17 agents across Duelist, Controller, Sentinel, Initiator roles.
2. **Match Engine**: Lobby system, auto-team balancing, phase timers (Buy phase, active round, spike detonation).
3. **Weapon Engine**: Spray patterns, recoil dynamics, skin customization, buy shop UI (`/vshop`).
4. **Map Engine**: Procedural maps (`ascent`, `split`, `bind`) and custom map creation wizard (`/vmapsetup`).

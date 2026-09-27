# Phase 2 Context: Core Refinement & Stability

## Phase Goal
Refine the core plugin environment, build system, weapon recoil/spray mechanics, and particle ability performance to ensure stability and high-performance competitive gameplay.

## Objectives
1. **Build & Dependency Synchronization**:
   - Align `pom.xml` properties (`java.version` to Java 21, `paper-api` target to Paper 1.21.4+ snapshot).
   - Ensure clean compilation with `mvn clean package`.
2. **Weapon Mechanics Audit**:
   - Audit `WeaponType.java` and `Weapon.java` recoil math, spray patterns, and spread reset timers.
3. **Particle & Ability Performance Tuning**:
   - Audit heavy tick-loop particle abilities (e.g. Viper's Pit, Astra Cosmic Divide, Phoenix Firewall) for performance optimizations.

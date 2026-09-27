# Phase 2 Research: Core Refinement & Stability

## Findings & System Gaps

### 1. Build System & Target Alignment
- **Current `pom.xml`**: Targets Java 17 and `paper-api:1.20.1-R0.1-SNAPSHOT`.
- **Target Runtime**: Paper 1.21.4 / Paper 1.21.11 with Java 21+.
- **Action Required**: Update `<java.version>` to `21` and update Paper API version to `1.21.4-R0.1-SNAPSHOT` or latest stable API snapshot.

### 2. Weapon Spray & Recoil Mechanics
- `src/main/java/com/valorantmc/weapons/Weapon.java`:
  - Contains spray recoil calculation algorithms.
  - Requires verification for reset decay timers when continuous firing stops.

### 3. Ability Particle Performance
- Abilities spawning persistent volumetric particle zones (e.g. Viper Pit, Smoke Orbs, Flame Walls) spawn particles via tick tasks.
- Optimizations needed: Raycast bounding box filtering and tick throttle when no players are nearby.

# Phase 2 Plan: Core Refinement & Stability

## Task Decomposition

### Task 1: Build & Dependency Modernization
- **Goal**: Update `pom.xml` build configuration to align with Java 21 and Paper 1.21.x API standards.
- **Files**: `pom.xml`, `run-server.bat`
- **Verification**: Run Maven package command to confirm error-free build.

### Task 2: Weapon Spray & Recoil Audit
- **Goal**: Audit `Weapon.java` spray formulas, spread reset decay rates, and bullet trajectory math.
- **Files**: `src/main/java/com/valorantmc/weapons/Weapon.java`, `src/main/java/com/valorantmc/weapons/WeaponType.java`
- **Verification**: Compile and verify spray math logic.

### Task 3: Ability Particle Performance Optimization
- **Goal**: Add tick throttling and spatial distance checks to particle density loops in persistent ability zones.
- **Files**: `src/main/java/com/valorantmc/agents/impl/ViperAgent.java`, `BrimstoneAgent.java`, `OmenAgent.java`
- **Verification**: Clean compile and verify particle spawning throttles.

## Verification Checkpoint
- Perform clean Maven compilation (`mvn clean package`).
- Ensure output artifact `target/ValorantMC-1.0.0.jar` is produced without compiler warnings or errors.

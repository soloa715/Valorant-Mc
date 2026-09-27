# Phase 3 Plan: Enhanced Features & Tooling

## Task Decomposition

### Task 1: Custom Map Setup Wizard & Visual Site Boundaries
- **Goal**: Expand `/vmapsetup` command with site boundary particle visualization, spawn clearance validation, and auto-save JSON configurations.
- **Files**: `src/main/java/com/valorantmc/commands/MapSetupCommand.java`, `src/main/java/com/valorantmc/managers/MapManager.java`
- **Verification**: Verify `/vmapsetup` subcommands compile cleanly and site boundaries render particles properly.

### Task 2: Resource Pack Packaging & Built-in Auto-Hosting
- **Goal**: Ensure built-in HTTP server in `ValorantMC` automatically packages and serves `ValorantMC-pack.zip` on port 8765 with zero external dependencies.
- **Files**: `src/main/java/com/valorantmc/managers/ResourcePackManager.java`, `ValorantMC.java`
- **Verification**: Run Maven build to confirm compilation and verify HTTP server binding.

## Verification Checkpoint
- Clean Maven compilation (`.\tools\maven\bin\mvn.cmd clean package`).
- Confirm `ValorantMC-1.0.0.jar` builds cleanly.

# Testing & Quality Assurance

## Automated & Verification Suite
- **Evaluation Reports**: `eval_report.json` tracks feature completeness, syntax validation, build integrity, and test scores.
- **In-Game Commands for Verification**:
  - `/valorant status <game>`: Live match state validation.
  - `/vmapsetup validate <map>`: Validates spawn point counts, bomb site boundaries, and world configurations before match execution.

## Build Verification
- `run-server.bat`: Auto-compiles plugin via `mvn clean package`, launches Paper server, and validates startup without exceptions.
- `build-plugin.bat`: Ensures clean compile artifact in `target/`.

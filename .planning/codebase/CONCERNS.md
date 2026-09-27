# Known Concerns & Tech Debt

## Version & Environment Alignment
- **Java Version Discrepancy**: `PROJECT_SUMMARY.md` specifies Java 21, while `README.md` recommends Java 25. Build scripts and compilation targets should remain aligned.
- **Paper vs Fabric API Versioning**: Paper 1.21.11 API compatibility must be maintained alongside Fabric 0.19.2 loader requirements.

## Asset Delivery & Resource Pack Hosting
- Custom weapons rely on `CustomModelData` and client resource pack downloading. If players decline or fail to download the pack, fallback vanilla models are displayed.

## Performance & Spatial Queries
- Particle heavy abilities (e.g. Viper's Pit, Astra Cosmic Divide, Phoenix Wall) require optimized server tick iteration to prevent lag spikes on larger player counts.

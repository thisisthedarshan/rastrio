# Rastrio Documentation

This directory contains the technical, security, testing, and visual specifications that support the repository-level `PRD.md`.

`PRD.md` remains the product and architecture source of truth. These documents provide deeper implementation detail for specific areas.

| Document | Purpose |
|---|---|
| `ARCHITECTURE.md` | System structure, module ownership, and dependency rules |
| `TD_SPEC.md` | Portable `.td` document format |
| `TCFG_SPEC.md` | Portable `.tcfg` printer-profile format |
| `TEXT_RENDERING_SPEC.md` | Text measurement, shaping, and raster/native rendering |
| `PREVIEW_SPEC.md` | Logical and physical preview contracts |
| `TESTING.md` | Repository-wide test strategy |
| `SECURITY.md` | Threat model and application-security requirements |
| `RESOURCE_LIMITS.md` | Resource, memory, archive, and input limits |
| `ESC_POS_NOTES.md` | Verified ESC/POS observations and printer quirks |
| `HARDWARE_TESTS.md` | Permanent manual hardware regression tests |
| `BRAND_GUIDE.md` | Rastrio brand identity and usage direction |
| `VISUAL_SYSTEM.md` | Production colors, themes, and iconography |

When documents disagree, resolve the conflict against `PRD.md` rather than silently inventing a new contract.

# Material Symbols Sharp

The `drawable/ic_*.xml` vectors are from Google's Material Symbols Sharp,
optical size 24, default weight. Upstream repository:
https://github.com/google/material-design-icons

Source path: `symbols/android/<name>/materialsymbolssharp/<name>_24px.xml`.
Downloaded 2026-10-16. Distributed under the upstream Apache License 2.0,
retained in this directory as `LICENSE`.

Adaptation: removed the Android theme-attribute tint and replaced the Android
white resource reference with literal white for Compose Multiplatform parsing.
Geometry is unchanged. Application code applies semantic tint. No remote icon
font or runtime network access is used.

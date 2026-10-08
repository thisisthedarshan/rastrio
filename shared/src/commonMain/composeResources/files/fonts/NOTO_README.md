# Pinned physical-preview representative fonts

Noto Sans Regular and Bold, version 2.008, unmodified static TrueType faces.
Noto Sans Mono Regular and Bold, version 2.007, provide controlled supplementary
glyphs only for the 60 safe CP437 scalars absent from the proportional faces.
Embedded copyright: Copyright 2015-2021 Google LLC. All Rights Reserved.
License: SIL Open Font License 1.1, preserved upstream in `NOTO_OFL.txt`.

Upstream repository: https://github.com/notofonts/noto-fonts
Pinned revision: `ffebf8c1ee449e544955a7e813c54f9b73848eac`

| Bundled file | Upstream path | SHA-256 |
| --- | --- | --- |
| `font/noto_sans_regular.ttf` | `hinted/ttf/NotoSans/NotoSans-Regular.ttf` | `b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5` |
| `font/noto_sans_bold.ttf` | `hinted/ttf/NotoSans/NotoSans-Bold.ttf` | `c976e4b1b99edc88775377fcc21692ca4bfa46b6d6ca6522bfda505b28ff9d6a` |
| `font/noto_sans_mono_regular.ttf` | `hinted/ttf/NotoSansMono/NotoSansMono-Regular.ttf` | `d9e2b23d19f8230be7146f409a52b1d23117e635e28f2e2892cf91b7382f325b` |
| `font/noto_sans_mono_bold.ttf` | `hinted/ttf/NotoSansMono/NotoSansMono-Bold.ttf` | `beb857bd799a114de7d5d90cc75cc69f4b6ee8d401f4ab20697e6f9719a66dfc` |

Reproduce by downloading the four paths above from
`https://raw.githubusercontent.com/notofonts/noto-fonts/ffebf8c1ee449e544955a7e813c54f9b73848eac/`.
The license is the same revision's `LICENSE` file.

All four faces use 1000 units/em, hhea ascent 1069, descent -293, line gap 0.
The physical renderer fits representative glyph ink within finalized native cells;
natural Noto advances never determine print wrapping, positions or paper advance.
Regular and Bold are the only styles supported by this phase. Supplementary
selection is a fixed scalar table verified against all safe CP437 characters;
it does not query operating-system fonts or influence native code-page selection. These are neither
printer ROM fonts nor the future authoritative raster/shaping backend.
No runtime font download or arbitrary host-font fallback is used.

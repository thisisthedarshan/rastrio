# Markdown compiler (Phase 2)

`MarkdownCompiler.compile(source, policy)` returns `MarkdownCompilation` containing
an existing `ThermalDocument` and ordered diagnostics. A fatal diagnostic returns
`document = null`; no partial document escapes. Compilation uses strings and
portable data only. It accepts no filesystem, network, printer, or rendering service.

## Parser

The implementation uses `org.jetbrains:markdown:0.7.7`, JetBrains' maintained
Apache-2.0 Kotlin Multiplatform parser, with `GFMFlavourDescriptor`. Its published
JVM, JS, and Wasm artifacts cover all configured Core targets; Android uses JVM
bytecode. The JVM POM declares only Kotlin stdlib at runtime. The version lives in
`gradle/libs.versions.toml`.

The parser source was inspected for iterative AST construction and cancellation
checkpoints. Rastrio counts cancellation checkpoints, lexer source reads
(including slices), and cached/filtered token reads in every sequential parser
against one deterministic work budget. Private list views meter reads without
copying tokens or changing upstream GFM syntax or parser order. A pre-parser
line count bounds eager line-view allocation. It traverses and bounds
the completed AST before semantic compilation. Parser AST classes remain private
implementation details; no public API accepts or returns them. No HTML generator
or renderer is invoked.

Upstream: https://github.com/JetBrains/markdown

## Semantic mapping

| Markdown | ThermalDocument v1 |
| --- | --- |
| Paragraph / soft newline | `Paragraph` / ordinary space |
| ATX and Setext heading | `Heading`, level 1–6 |
| Bold / emphasis / strike | `Strong` / `Emphasis` / `Strike` |
| Inline code | `InlineCode`, normalized code-span whitespace |
| Explicit hard break | `LineBreak` |
| Fenced / indented code | `CodeBlock`, language when present, literal code |
| Unordered / ordered / nested lists | `UnorderedList` / `OrderedList`, start number and nested blocks |
| Checked / unchecked task | `ChecklistItem.checked`, never checkbox glyphs |
| Block quote / separator | `Quote` / `Separator` |
| Inline / reference / automatic link | `Link`, semantic destination and content |
| Image | `Image(ExternalAssetReference(...), LEFT, AutoSizing, altText)` |
| GFM table | `Table`, column alignments, header and padded rows |
| Raw HTML | Literal `Text` and `MD101` warning |

Definitions resolve globally; the first definition wins. Undefined references
remain literal Markdown. CRLF and CR are normalized to LF. Compiler diagnostics
use offsets in that normalized string, measured in UTF-16 code units, with an
exclusive end. The compiler sets no metadata title from headings.

`.td` v1 stores images as blocks. Direct paragraph images split the paragraph
into text/image/text blocks in source order. Relative paths, HTTP(S), file URLs,
and other destinations remain unresolved identities; none are opened or fetched.
Images inside inline-only containers (formatting, links, headings, table cells)
remain literal Markdown with `MD102`, because those containers cannot store an
image block. Mixed plain/task lists become consecutive list/checklist runs.
Ordered tasks retain checklist state with a degradation warning for numbering.
Link/image titles have no v1 document field and produce a degradation warning.
GFM extra table cells beyond the header width are omitted with a warning.

## Diagnostics and compatibility

These code meanings are the public Phase 2 compiler contract. Additive new codes
are allowed; changing an existing meaning requires a documented compatibility
change. Messages are descriptive and contain no user document content; consumers
should branch on code/severity rather than message text. Diagnostics are not
persisted in `.td` v1.

| Code | Severity | Meaning |
| --- | --- | --- |
| `MD100` | Error | Invalid Unicode or parser rejection |
| `MD101` | Warning | Raw HTML preserved literally |
| `MD102` | Warning | Unsupported construct preserved literally |
| `MD103` | Warning | Semantics degraded by the existing document model or GFM table rules |
| `MD120` | Error | Source byte limit exceeded |
| `MD121` | Error | Source lines, parser work, AST, nesting, canonical nodes, strings, or table limit exceeded |
| `MD201` | Warning | Image needs explicit higher-level asset resolution |

## Resources

Trusted `MarkdownResourcePolicy` owns the 8 MiB UTF-8 source ceiling, 250,000 AST
nodes, 100,000 source lines, at most 128 AST nesting levels, and 50,000,000 parser work units. Source
UTF-8 size and surrogate validity are checked without allocating a byte-array
copy. Work units count parser checkpoints, source reads, slice creation, and
materialized slice characters, and cached/filtered token reads; they are not a wall-clock timeout. AST traversal
is iterative. Lines count CRLF once and standalone CR/LF once, including the
final line, with a checked `Long` increment before normalization/parsing. The
line ceiling is independent of bytes and bounds the eager per-line allocations.
Recursive semantic conversion is limited by the validated AST.

The existing `TdResourcePolicy` supplies canonical block, inline, nesting, string,
and table limits (including the default 64 KiB per string). Table dimensions and
padded-cell totals are checked with `Long` before allocating padded rows. An
oversized token/code block fails rather than being truncated. Neither policy is
read from author input. Resource limits are runtime ceilings, not `.td` schema
changes or promises that every 8 MiB input will compile.

## Tests

The 16 PRD fixtures live in `test-fixtures/markdown`. Exact expected portable
documents and diagnostics are handwritten in common `MarkdownGoldens.kt`; JVM
tests ensure the committed `.md` inputs match those common cases. Common tests
also cover malformed syntax, source/AST/work/document/table boundaries, deep
lists/quotes, long code/tokens/delimiters, many references/images, literal HTML,
reference precedence, newline normalization, privacy, table code/quote edge cases,
and `.td` round trips. The actual repository README also compiles deterministically
and round-trips through `.td`. Browser resource tests have a 10-second Mocha
timeout so full-size adversarial cases also run in Wasm.

A JVM loopback server test checks that image/link/HTML references produce no
connection. Existing and nonexistent filesystem references compile identically
as references, including a non-image file. Together with the compiler API,
source inspection, dependency metadata, and architecture verification, this
provides evidence for the absence of hidden I/O in the compilation path.

Text metrics, layout, printer preparation, preview, encoding, transports, asset
resolution, and UI belong to later phases.

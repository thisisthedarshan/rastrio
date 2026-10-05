# Rastrio — Agent Instructions

Rastrio is an Apache-2.0 Kotlin Multiplatform thermal-printing stack and application.

This file is intentionally concise. Use it as the repository operating contract for Codex and other coding agents. The product and architecture specification lives in `PRD.md`; do not duplicate it here.

## Read first

For any non-trivial task, read the relevant parts of:

- `PRD.md` — scope, phases, architecture, invariants, Definition of Done, and the full engineering rules in **Section 104**.
- `docs/ARCHITECTURE.md` — module responsibilities and dependency direction.
- `docs/TD_SPEC.md` — `.td` format.
- `docs/TCFG_SPEC.md` — `.tcfg` printer-profile format.
- `docs/TEXT_RENDERING_SPEC.md` — text layout/rendering contract.
- `docs/PREVIEW_SPEC.md` — logical/physical preview contract.
- `docs/TESTING.md` — test strategy.
- `docs/SECURITY.md` and `docs/RESOURCE_LIMITS.md` — untrusted-input and bounded-resource rules.
- `docs/ESC_POS_NOTES.md` and `docs/HARDWARE_TESTS.md` — verified protocol/hardware behavior.
- `docs/BRAND_GUIDE.md`, `docs/VISUAL_SYSTEM.md`, and `assets/ASSETS.md` when changing product visuals, themes, icons, screenshots, or brand assets.

If a referenced document has not been created yet, use `PRD.md` as the governing baseline. Do not silently resolve contradictions between specifications, tests, and implementation. If two governing documents appear to conflict, stop changing the affected contract, identify the conflict, and preserve the higher-authority rule until the specifications are deliberately reconciled.

## Optional agent tooling

Rastrio contributors are **not required** to install or use Serena, Graphify, Ponytail, or any other agent-specific tooling. These tools are optional productivity aids only. A contributor or coding agent without them MUST still be able to work on the repository using normal file inspection, search, IDE, compiler, test, and Git tooling.

Use the following guidance **only when the corresponding tool is installed and available in the current environment**. Do not fail, block, or defer a task merely because one or more of these tools are unavailable.

### Tool authority and fallback

The repository's source code, tests, `PRD.md`, and relevant documents under `docs/` remain authoritative. Optional tools may help discover or modify code, but they do not override repository specifications, architecture invariants, security/resource rules, tests, compiler output, or verified hardware behavior.

When an optional tool is unavailable:

- use normal repository search, targeted file reads, IDE/language-server navigation, and compiler/test feedback instead;
- keep reads scoped to the task rather than scanning unrelated parts of the repository;
- do not ask the contributor to install optional tooling unless they explicitly want to use it.

When several optional tools are available, prefer the following division of responsibility rather than using all of them for the same job.

### Graphify — repository topology and cross-cutting discovery

When Graphify is available, use it primarily for **coarse-grained repository discovery**:

- identify the modules, files, concepts, and dependency paths relevant to a task;
- answer cross-cutting questions such as which subsystems participate in a feature or transformation;
- narrow the initial search space before reading implementation files;
- prefer scoped graph queries over broad repository-wide file reads when that is sufficient.

Graphify is a discovery aid, not a source of normative behavior.

- Do not rely on Graphify summaries or inferred graph edges instead of reading the relevant `PRD.md` or `docs/` specification.
- Do not treat inferred relationships as established architecture when the source or specification says otherwise.
- After Graphify identifies the likely implementation area, inspect the relevant source and tests directly before changing behavior.
- Avoid querying or indexing irrelevant build outputs, caches, generated artifacts, or large unrelated asset trees where configuration permits.

When both Graphify and Serena are available, normally use **Graphify first for broad topology**, then **Serena for exact symbol-level inspection**.

### Serena — symbol-level code navigation and semantic editing

When Serena is available, use it primarily for **fine-grained code intelligence**:

- locate symbol definitions, references, implementations, callers, and related symbols;
- inspect only the symbol bodies and surrounding code needed for the task;
- use semantic rename/refactoring/editing operations when they are safer than textual replacement;
- use it to reduce unnecessary whole-file or repository-wide reads.

Serena is not authoritative for project behavior.

- Confirm architectural or persistent-format decisions against the relevant repository specifications.
- Validate semantic edits with the normal compiler, Gradle tasks, and tests.
- For Kotlin Multiplatform, Gradle, generated code, source-set wiring, `expect`/`actual`, Compose compiler behavior, or other areas where semantic tooling may be incomplete, fall back to direct source/build-file inspection and build/test feedback.
- Do not perform a broad refactor merely because Serena makes it easy; the requested change must still satisfy the repository's change-discipline rules.

### Ponytail — implementation minimalism and over-engineering review

When Ponytail is available, use it as an **implementation-discipline aid**:

- prefer reusing existing project code and platform/standard-library capabilities;
- avoid unnecessary dependencies, wrappers, abstractions, indirection, and speculative future-proofing;
- make the smallest coherent change that fully satisfies the task;
- use Ponytail review/audit capabilities where useful to identify accidental over-engineering.

Ponytail's minimalism is subordinate to Rastrio's documented architecture and correctness requirements.

It MUST NOT be used to remove or bypass:

- required module boundaries or domain abstractions;
- validation, diagnostics, security controls, or resource limits;
- compatibility/migration behavior;
- deterministic processing requirements;
- tests or testability seams;
- the `ThermalDocument` → `LogicalDocument` → `PreparedPrint` pipeline;
- the separation between physical preparation, preview, ESC/POS serialization, and transport;
- any other invariant required by `PRD.md` or the relevant `docs/` specification.

If a simpler implementation would violate a documented invariant, keep the invariant.

### Preferred agent workflow when these tools are available

For a non-trivial implementation task, use the tools in this order when useful:

1. Read this file and the relevant normative specification(s).
2. Use Graphify, if available, to identify the affected subsystem and narrow the search space.
3. Use Serena, if available, to inspect the exact symbols, references, and implementations involved.
4. Read the relevant source and tests directly before editing.
5. Implement the smallest coherent change, applying Ponytail principles if available without weakening architecture, correctness, security, compatibility, or tests.
6. Run the required targeted tests and repository verification.
7. Use Ponytail review, if available and useful, as a final over-engineering check; do not accept suggestions that conflict with repository rules.

## Priorities

1. Android is the implementation and release priority.
2. Core logic and primary Compose UI are shared.
3. Desktop/JVM is encouraged for rapid Core/UI development.
4. Web must remain architecturally viable, but Web-specific tooling must not block Android before the Web phase.
5. Do not add iOS-specific work unless project requirements explicitly change.

## Architecture invariants

Preserve this pipeline:

```text
Authoring input
    ↓
ThermalDocument
    ↓
LayoutConstraints + core-layout
    ↓
LogicalDocument
    ↓
PrinterProfile + PrintOptions + core-printer
    ↓
PreparedPrint
    ├──→ core-preview → PrintPreview
    └──→ core-escpos  → encoded bytes → platform transport → printer
```

Non-negotiable rules:

- `.td` is printer-independent and never stores ESC/POS commands, printer code pages, physical printer widths, transport identities, or transport configuration.
- `core-*` modules must not depend on Android, Compose, `shared`, or application modules.
- `core-layout` owns logical geometry under explicit `LayoutConstraints`.
- `LogicalDocument` is protocol-independent but may be target-constrained.
- `core-text` owns shared text measurement, shaping, coverage, font-metric, and raster-text contracts.
- `core-printer` owns all printer-specific preparation and strategy decisions.
- `PreparedPrint` is the authoritative immutable physical print plan.
- Physical preview and ESC/POS serialization consume the same `PreparedPrint` value and the same finalized immutable prepared-artifact content.
- `core-preview` must not independently recreate physical layout or regenerate finalized prepared artifacts.
- `core-escpos` serializes decisions already present in `PreparedPrint`; it must not re-layout, re-wrap, re-shape, re-dither, re-rasterize, re-segment, choose code pages, or choose native/raster strategy.
- Neither `core-preview` nor `core-escpos` may silently reconstruct missing/corrupt output-affecting prepared data. Fail with a structured diagnostic instead.
- Printer behavior comes from `PrinterProfile` or validated overrides, never scattered model-specific constants.
- H50i-specific behavior must not leak into generic Core code.
- Do not promote undocumented or unverified H50i behavior into maintained capability data. Distinguish documentation-derived, byte-verified, hardware-observed/verified, and inferred behavior according to `docs/ESC_POS_NOTES.md`.
- Platform-specific types must not leak through portable Core APIs.

## Platform and application ownership

- Shared UI uses Compose Multiplatform by default.
- Shared Presentation coordinates Core; it does not duplicate Core algorithms.
- `PrinterProfile` is portable printer capability/model data and is serialized by `.tcfg`.
- `PrinterInstance` is shared application state representing one physical printer known to the user; it is not part of `.tcfg`.
- `PrintOptions` are choices for one preparation operation; they are not printer capabilities and are not serialized into `.tcfg`.
- Live Bluetooth/USB/browser/device/session objects remain platform-owned and must not be stored in portable Core models.
- Android Bluetooth/USB implementations belong in `androidApp`.
- Desktop transports belong in `desktopApp` when implemented.
- Browser hardware integrations belong in `webApp`.
- Do not create platform-specific copies of shared screens without a concrete requirement.
- Transport code handles connection/session/write concerns only; it must not understand Markdown, layout, tables, images, QR semantics, text shaping, or dithering.
- Printer buffer/raster-band constraints and transport write-chunk constraints are separate concerns.
- Changing transport must not require re-layout, re-shaping, re-rasterization, or re-dithering.

## Text, Markdown, images, assets, and network I/O

- Shared/Core image APIs must use platform-neutral representations.
- Do not expose Android `Bitmap` or platform graphics objects through Core APIs.
- Text measurement, shaping, native-font handling, and raster fallback follow `docs/TEXT_RENDERING_SPEC.md`.
- Compose text measurement is not authoritative printer geometry.
- Core Markdown parsing must not perform hidden filesystem or network I/O.
- Raw/unsupported Markdown HTML is literal text plus a structured diagnostic in v1. It must never execute or trigger DOM/script/style/network/resource behavior.
- External assets require an explicit higher-level resolution step. Parsing Markdown or `.td` must never fetch them implicitly.
- Image and raster transformations must be deterministic for identical controlled inputs/options.

## Portable-format rules

- `docs/TD_SPEC.md` and `docs/TCFG_SPEC.md` define the public portable formats. Do not infer new fields or compatibility behavior from implementation convenience.
- Portable `.td` and `.tcfg` JSON rules are authoritative over parser-library defaults or leniency.
- Reject prohibited syntax, duplicate keys, unknown v1 properties, illegal nulls, unsupported versions/discriminators/identifiers, and other invalid forms required by the relevant specification.
- Duplicate-key and unknown-property validation must occur before a lossy parser representation can erase that evidence.
- Unknown future format/schema versions must not be silently interpreted as current versions.
- Persistent-format changes require the relevant specification update, compatibility/migration analysis, and tests in the same change.

## Print-job safety

- Successful byte transmission is not proof that physical paper output completed unless the hardware actually confirms it.
- Track partial/ambiguous transmission where practical.
- Never automatically retry a job when output may already have been partially transmitted.
- Support cancellation and clean resource release where the platform permits it.
- A reconnect or transport retry must not be treated as proof that earlier bytes produced no physical output.

## Security and privacy

Treat `.td`, `.tcfg`, Markdown, images, imported fonts/assets, device metadata, archive paths, and persistent data crossing an import/migration boundary as untrusted input.

- Enforce path, archive-expansion, entry-count, JSON, image, font, document-complexity, preview, prepared-artifact, and memory/resource limits from the first implementation that handles the resource.
- Prevent path traversal, absolute-path extraction, alternate-separator traversal, duplicate normalized paths, and ZIP-bomb style expansion.
- Use checked arithmetic for externally influenced size/dimension calculations.
- Use bounded-memory processing for long documents, previews, raster data, prepared artifacts, and encoded print streams.
- Do not build one giant print-job `ByteArray` or receipt bitmap when output can be streamed/banded.
- Do not log printable user content by default: no Markdown bodies, notes, QR payloads, images, private asset bodies, tokens, or document contents.
- Do not introduce hidden network requirements into offline authoring, parsing, preview, preparation, or printing workflows.
- Imported `.tcfg` files must not be able to inject executable code or arbitrary/raw ESC/POS command programs.
- Application-controlled prepared-artifact backing stores must use bounded, private, lifecycle-managed storage; untrusted filenames/paths must not become host filesystem paths.

## Testing

Testing is part of implementation, not cleanup.

- Add/update tests in the same change as production behavior.
- Every practical bug fix should include a regression test.
- Deterministic Core transformations require unit and/or golden tests.
- Raster algorithms require golden tests, including odd widths and widths not divisible by 8.
- ESC/POS features require byte-level golden tests where practical.
- Persistent formats require round-trip, invalid-input, strict-reader-path, versioning, compatibility, and migration tests where applicable.
- Resource/security controls require boundary and over-limit regression tests.
- Preview/print tests must prove both consumers observe the same finalized `PreparedPrint`/prepared-artifact content rather than duplicated algorithms.
- Unsupported significant content must produce diagnostics rather than silently disappear.
- Hardware-facing behavior should add/update a numbered procedure in `docs/HARDWARE_TESTS.md` where practical.

Before completion, run the narrowest relevant tests plus the repository's mandatory verification task. Until a dedicated root verification task exists, run the applicable Gradle checks and `./gradlew check` where supported. Do not report success while required tests are failing. Report exactly what was executed and identify any verification that could not be run.

## Licensing

Rastrio is licensed under the Apache License 2.0.

The following rules are mandatory:

- New project-owned source files MUST use the same full Apache-2.0 license-header format already used by existing Rastrio source files.
- New Rastrio-owned source files MUST use the same copyright-holder text and year convention as the existing Rastrio source headers.
- Do NOT replace existing full Apache-2.0 headers with SPDX-only headers merely for stylistic consistency.
- Do NOT mix full Apache-2.0 headers and SPDX-only headers within Rastrio-owned source files unless the repository deliberately changes its licensing convention.
- Preserve all existing copyright notices and license headers.
- Do NOT remove, rewrite, replace, or claim ownership of third-party or contributor copyright notices.
- Do NOT add Rastrio license headers to generated files, Gradle-generated files, vendored code, copied upstream sources, third-party code, binary assets, or files whose format makes a source header inappropriate.
- Do NOT add `Author:`, `Created by`, or similar per-file authorship metadata. Git history is the authoritative record of authorship.
- When modifying a file that already contains an appropriate license header, preserve that header unless there is an explicit licensing reason to change it.
- When importing or adapting third-party code, preserve its original licensing information and verify that its license is compatible with Apache-2.0 before adding it to the repository.
- Do NOT assume that code generated by an AI agent changes copyright ownership or licensing requirements; generated project code must follow the same Rastrio licensing rules as manually written project code.
- If the correct licensing treatment of a file is uncertain, do not invent or alter a license notice. Surface the issue for review instead.

## Gradle and dependencies

- Use Gradle Kotlin DSL (`*.gradle.kts`) only.
- Keep dependency/plugin versions in `gradle/libs.versions.toml` unless a toolchain constraint requires otherwise.
- Prefer `build-logic/` convention plugins for repeated KMP/Compose/test configuration once repetition justifies them.
- Before adding a dependency, check target support, maintenance status, license compatibility, deterministic/offline behavior where relevant, and F-Droid/FOSS impact.
- Avoid mandatory proprietary Google Play service dependencies.
- Do not add dependencies merely to avoid implementing small deterministic Core logic.
- Do not add speculative cross-platform abstractions solely because another target may need them later.

## Change discipline

For each task:

1. Read the relevant specification and existing implementation/tests.
2. Inspect the current repository state before editing; do not assume files/modules/tasks exist merely because a specification proposes them.
3. Make the smallest coherent change that satisfies the task.
4. Preserve module boundaries and persistent-format compatibility.
5. Prefer immutable domain models and deterministic pure Core transformations.
6. Do not introduce speculative abstractions solely for hypothetical future platforms/features.
7. Do not rewrite unrelated code or reformat unrelated files.
8. Add/update diagnostics and tests.
9. Update the relevant specification when a persistent or architectural contract changes.
10. Run relevant verification and report what was actually executed.

If a requested change conflicts with a documented architectural invariant, format/schema contract, compatibility guarantee, security rule, or later-phase scope boundary, make the conflict explicit rather than silently changing the contract.

## Git and commit discipline

- Do NOT commit, push, force-push, rebase, reset published history, or otherwise mutate repository history unless explicitly requested.
- Keep each proposed commit focused on one coherent logical change.
- When asked to commit, or when providing a completion report for a substantial uncommitted change, provide a recommended commit message following the Conventional Commits format:

  `<type>(optional-scope): <description>`

- Use the most accurate commit type:
  - `feat` — new user-visible or externally meaningful functionality;
  - `fix` — bug fix;
  - `refactor` — implementation restructuring without intended behavior change;
  - `test` — tests only;
  - `docs` — documentation only;
  - `build` — build system, Gradle configuration, dependencies, or packaging;
  - `ci` — continuous-integration configuration;
  - `perf` — performance improvement;
  - `chore` — repository maintenance or engineering work that does not fit a more specific type.
- Do NOT use `feat` merely because a change is large.
- The subject MUST be concise, imperative, and describe the completed change rather than the activity performed.
- Do not end the subject with a period.
- For non-trivial commits, include a body separated from the subject by a blank line.
- The body SHOULD explain the significant architectural or behavioral changes and why they were made. Do not merely dump a list of changed files.
- Include relevant verification performed when useful, especially for architectural, build, migration, security, or release-sensitive changes.
- Breaking changes MUST use the Conventional Commits breaking-change form (`!` and/or a `BREAKING CHANGE:` footer) and must not be introduced silently.
- If a change spans multiple unrelated concerns, recommend splitting it into separate commits rather than inventing one broad commit message.
- Do not claim tests or verification were run unless they were actually executed successfully.
- Unless explicitly asked to create the commit, leave changes uncommitted and include the recommended commit message in the completion report.

## Definition of done

A change is not complete merely because it compiles. Confirm that the requested behavior exists, relevant tests pass, architecture boundaries remain intact, error/diagnostic behavior is defined, preview reflects physical-output decisions, platform details have not leaked into portable Core APIs, persistent-contract changes are documented and compatibility-aware, security/resource implications have been addressed, and no hidden network requirement has been introduced into an offline workflow.

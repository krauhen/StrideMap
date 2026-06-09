# Workflow guidance

## Safety policy

Allowed without asking:

- Read/list files in this repository.
- Edit requested documentation files.
- Run non-destructive inspection commands.

Ask first before:

- Any dependency change, including Gradle metadata and lockfiles.
- Adding, removing, or upgrading packages or plugins.
- Heavy or expensive command runs not explicitly requested.
- Any code, Docker, CI, infrastructure, secret, signing, or data-artifact change.

Never without explicit one-off permission:

- Changing Docker/container setup.
- Changing CI/CD workflows.
- Changing infra/deployment assets.
- Deleting, moving, or renaming files.
- Creating or modifying secrets, signing keys, certificates, or `.env*` content.
- Adding copied personal location traces, private map screenshots, large route datasets, personal details, names, contact information, or addresses.

## Project status

- StrideMap is an early-stage personal Android GPS/location-history app.
- Current v1 is a real GPS reference-track capture app with Capture/Tracks/Map tabs, Settings via top-corner gear, public GPX output, foreground-service capture, and osmdroid map display.
- `agentic/knowledge/SPEC.md` is the current single source document for implementing the application as it exists now.
- `agentic/knowledge/features/init-creating-all-defined-components.md` and session summaries are source material/history for `SPEC.md`, not the preferred implementation handoff when `SPEC.md` is present.
- Keep remote sync, API, PostgreSQL, adaptive stationary tracking, and SQLite route storage as deferred/future context unless explicitly rescoped.
- Treat notes as planning context unless implementation files or explicit user instructions say otherwise.

## Agent workflow norms

Before editing:

- Inspect `git status` and `git diff` when the directory is a git repository.
- Prefer minimal, focused changes.
- Do not touch unrelated files.

Definition of done for documentation tasks:

- Keep changes limited to requested documentation paths.
- Verify copied context and forbidden references are removed when requested.
- Do not run expensive tests for docs-only changes.

Documentation updates:

- If assumptions or product scope change, update relevant documentation in the same task where feasible.
- When older docs conflict with the v1 feature source of truth, update the docs rather than preserving stale simulator-first or SQLite-first framing.

## SPEC.md maintenance

- `agentic/knowledge/SPEC.md` is the canonical implementation specification for the full current application behavior.
- When the user asks to update or regenerate `SPEC.md`, first read the relevant guidance, then synthesize from:
  - current implementation files and tests;
  - all relevant files in `agentic/knowledge/features/` as first-class product/behavior source material;
  - recent `agentic/sessions/summaries/` as first-class decision/validation source material;
  - relevant `agentic/sessions/raw/` files when summaries/features are insufficient, when the user emphasizes session detail, or when validation/debug rationale matters.
- Write `SPEC.md` as an implementation-ready technical specification, not a conversational summary.
- Use industry-standard terminology for Android, Kotlin, Compose, lifecycle, persistence, storage, permissions, foreground services, GPX, testing, and validation.
- Distinguish implemented behavior, documented product intent, deferred/non-goals, and pending validation.
- Include architecture, data contracts, lifecycle/state machines, storage/recovery invariants, permission/error behavior, UI/navigation behavior, settings defaults, testing strategy, validation status, and known caveats.
- Prefer stable product terminology over brittle internal names; mention internal naming caveats only where they affect implementation.
- Do not include personal location traces, private paths beyond generic Android storage paths, screenshots, secrets, or copied personal data.
- If a current implementation detail conflicts with older session notes, record the current implementation/spec behavior as the conflict winner and preserve older details only as historical/deferred context when useful.

Session notes:

- Raw session notes may be detailed enough to preserve source context.
- Before restoring prior context, always read the list of files in `agentic/sessions/summaries/` first to get an overview of what has been done in the past, then read the relevant summaries.
- Session summaries must be much shorter than raw notes and stay within 50-75 lines unless the user explicitly asks for a denser handoff.

## Working out a feature or product description

- Start with locked decisions.
- Explicitly list what is decided versus open.
- Add scope and non-goals to prevent creep.
- Produce a full impacted-artifact map when implementation is expected.
- Define data contracts first when relevant.
- Include Android permissions, foreground-service behavior, storage invariants, and sync assumptions only when confirmed.
- Add execution sequence in dependency order for future implementation.
- Add go/no-go gates with measurable checks.
- Add a requirement-to-verification matrix.
- Map every requirement to tests/scripts and pass criteria when implementation exists.
- Keep a Q&A/addendum log.
- Mark the newest section as conflict winner when decisions evolve.
- Treat a feature plan as implementation-ready only when every requirement has an owning test or script.

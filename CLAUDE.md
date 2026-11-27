# RimWorld Mod Manager

Personal mod manager for RimWorld. Requirements: `docs/requirements.md`.

Stack: Kotlin + Ktor backend, React + TypeScript (Vite) frontend, one Gradle project; the full build serves the client from the same server. Linux (Kubuntu).

## Roles

- **User** — product owner and architect. Decides *what* gets built and the trade-offs: open questions, scope, tech stack, UI taste. Reviews and accepts work.
- **Claude** — implementer and engineer. Proposes options with a recommendation, writes code, tests and scripts, runs and verifies them. Flags gaps or contradictions in the requirements instead of silently working around them.

## Source of truth

- `docs/requirements.md` is the master copy of the requirements and evolves together with the code. The original Claude Docs document (https://claude.ai/artifact/VZ991vciSe2xpPT6ikjoN2) is an archive only.
- Decisions are recorded in the requirements: a resolved item moves out of "Open questions" into the relevant section, with a short "why".

## Workflow

1. Work in tasks the size of one MVP step.
2. Non-trivial task → short plan first (files, approach, how it will be verified). Small things → just do them.
3. For pure logic (export pipeline, transitive dependencies, translation matching, sync rules, tri-state folders): write tests from the spec first, then implement. No up-front test review: the user reviews the whole project every few iterations, or limits review to user acceptance tests.
4. Verify with tests and, where relevant, real data. Report honestly what was verified and what was not.
   - UI is tested manually by the user for now: no browser automation (Playwright etc.). Claude checks the API and typechecks the client, then hands over a short manual test checklist.
5. Commits:
   - **Docs** (`docs/`, `CLAUDE.md`): commit without asking, in a separate docs-only commit, as soon as an agreed decision has been documented.
   - **Code**: commit only when the user says so. Offer a commit as soon as a step is verified, so changes do not pile up.
   - **Size**: small commits; a good commit is one its first line fully describes.
   - **Messages**: about the change only. No `Co-Authored-By`, session links or other trailers.

## Permissions

- Without asking: read/write project files, run tests and builds, create helper scripts.
- Writing the real `ModsConfig.xml` (through the app's activation, with its backup) is allowed: a broken mod list is recoverable, since every save stores its own list and the game offers to load it.
- Only with explicit consent: anything else touching the real game — files in RimWorld or Workshop folders, launching RimSort. During development work on copies.
- New dependencies or stack changes: discuss first.

## Subagents

No standing team. A previous project used separate implementer / blind-tester / reviewer agents, and the token cost far outweighed the benefit at this scale. Claude does the work directly and spawns an agent only when a task is:

- **bulk and independent**, e.g. the one-off classification of a few hundred mod descriptions (2–4 parallel agents, each returning only a mapping table);
- **needs fresh eyes**, e.g. an independent review of the code that writes game files, before the first real activation (via `/code-review`, on the user's command);
- **research that would flood the main context**, e.g. digging through RimSort / `ModsConfig.xml` format docs.

Before spawning, say why and how many agents.

## Communication

- Conversation in Russian; code, comments, commit messages and docs in English.
- Be concise: a recommendation, not a survey of every option.

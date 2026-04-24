# AGENTS.md

## Project

This repository is `smart-ticket-platform`, a Java Spring Boot smart ticket system.

## Current task

Refactor the Agent module according to:

- `docs/agent-refactor-checklist.md`
- `docs/agent-refactor-implementation-plan.md`

## Rules

- Do not rewrite the whole Agent module in one pass.
- Execute only the phase explicitly requested by the user.
- Preserve existing `/api/agent/chat` behavior unless the current phase explicitly changes it.
- Do not implement SSE, rate limiting, session lock, or high-pressure governance before the requested phase.
- Write clear Chinese comments for new classes, interfaces, enums, core public methods, important fields, and complex branches.
- Use `/** */` for class/interface/enum/core public method comments.
- Use `//` for fields and complex method internals.
- Do not add meaningless comments for simple getters, setters, constructors, assignments, or obvious code.

## Build and test

Before finishing each phase, run:

```bash
mvn -q -pl smart-ticket-agent -am test
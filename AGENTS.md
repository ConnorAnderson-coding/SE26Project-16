# AGENTS.md

## Project overview

This repository contains a campus activity platform.

Main modules:

- Frontend: `campus-activity`
- Backend: `campus-activity/backend`
- Planned clustering service: `clustering-service`

Technology stack:

- Frontend: React, Ant Design, Vite, Vitest
- Backend: Spring Boot 3.3.5, Java 17, Spring Data JPA
- Clustering service: Python, FastAPI, pandas, numpy, scikit-learn
- Current local database: H2
- Planned integration database: MySQL
- API style: REST JSON
- UI and documentation language: Chinese

## Current development goal

Implement the community clustering feature in small, independently reviewable phases.

## General rules

- Inspect existing code before editing.
- Present a file-level implementation plan before making changes.
- Do not modify unrelated files.
- Do not delete existing behavior without explicit approval.
- Do not add production dependencies without explicit approval.
- Do not hard-code passwords, API keys, access tokens, service URLs, or database credentials.
- Do not run `git push`.
- Do not rewrite Git history.
- Do not execute Git commits unless explicitly requested.
- Do not generate or commit build output.
- After every task, report changed files and test results.

## Community clustering rules

- Iteration 2 uses K-Means hard clustering.
- One user belongs to exactly one community in one clustering version.
- Use `random_state=42` for deterministic tests.
- Use PCA for 2D visualization in iteration 2.
- Return x and y coordinates normalized to the range 0-100.
- The browser must not call the Python clustering service directly.
- Spring Boot handles permissions, persistence, task orchestration, and public REST APIs.
- Python handles feature preprocessing, clustering, and dimensionality reduction.
- Do not implement Redis in the first delivery.
- Do not implement the community message board in the first delivery.
- Do not invent browsing data if the repository does not currently collect it.
- Never expose passwords or authentication secrets.

## Required checks

Frontend:

- `npm.cmd test`
- `npm.cmd run lint`
- `npm.cmd run build`

Backend:

- `mvn test`

Python clustering service:

- `python -m pytest`

## Completion report

After each implementation task, report:

1. Changed files
2. Main implementation decisions
3. Commands executed
4. Test results
5. Remaining risks
6. Items deliberately not implemented

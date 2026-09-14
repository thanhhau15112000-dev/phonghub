---
name: create-github-pr
description: Use when creating a GitHub pull request for this repo. Defines what a PR must contain (title, description template, checklist) and the push + gh commands.
---

# Create GitHub PR

How to open a clean, reviewable pull request against `main` for this repo.

## Branch
Work on a branch named `lesson/<NN>-<kebab-slug>` (e.g. `lesson/02-setup-simple-spring-ai`). Never commit directly to `main`.

## Pre-flight
- `./gradlew build` passes (compiles + runs all tests).
- `git status` clean, no secrets/API keys staged.

## PR title
Conventional-commit prefix + short summary:
- `feat: add product suggestion endpoint`
- `fix: ...` / `refactor: ...`

## PR description — copy this template

```markdown
## Summary
<!-- What does this PR do? 1-3 sentences. -->

## Changes
<!-- Bullet list of key changes. -->

## Testing
<!-- How was it tested? e.g. `./gradlew test`, manual curl. -->

## Checklist
- [ ] Build passes (`./gradlew build`)
- [ ] Tests pass
- [ ] No secrets committed

## API examples (if applicable)
<!-- Sample request/response. -->
```

## Commands

```bash
git push -u origin <branch>
gh pr create --base main --head <branch> --title "feat: ..." --body "..."
```

## Must-haves for every PR
1. Clear, prefixed title.
2. Description with Summary / Changes / Testing.
3. Green build + tests.
4. No secrets.

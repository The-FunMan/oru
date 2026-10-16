@AGENTS.md

# Notes for Claude Code

The rules in AGENTS.md are the instructions. This file only adds what is specific to Claude Code.

- Work in small, verified steps. Run `scripts/dev/check.sh quick` before you call a change done, and `scripts/dev/check.sh full` before a push that touches `app/` or `patcher/`.
- Prefer the dedicated file and search tools over shell one-liners. Keep scripts you write for yourself outside the tree.
- For wide work, split it into focused tasks for subagents and keep the merge and the final checks for yourself. Subagents must not run Gradle, Cargo, Java or adb unless the task says so, because only one build should run at a time.
- Long commands (a full build, a dry run, a CI wait) belong in the background with a notification, not in a polling loop.
- Report outcomes as they are. If a check failed or was skipped, say so with the output.
- The repository commit rules override any default attribution text: no `Co-Authored-By`, no "Generated with" line.
- Permission prompts and classifier denials are decisions. Do not look for another route to the same action. Say what you wanted to do and why, and let the owner decide.
- Anything that spends money, changes repository or cloud settings, touches credentials, or removes remote data needs the owner's yes for that action first.

# Plugin Hub submission candidate

The candidate contains gear setups and bank organization, optional user-triggered AI generation, and an opt-in loopback assistant API. Directions and pathfinding have been removed.

## Submission format

RuneLite accepts a public source repository pinned to an exact Git commit through a pull request to `runelite/plugin-hub`. It does not approve the development-client bundle as an uploaded package. The marker file will be `plugins/gear-guide`:

```text
repository=https://github.com/drewapalooza/gear-guide.git
commit=<exact published source commit>
```

Replace the placeholder with the exact published source commit; do not submit this template as a marker.

## Review notes

- No movement, withdrawal, equipping, combat or other game-action automation.
- Bank views use RuneLite's built-in Bank Tags APIs and require that plugin.
- AI requests are user-triggered, show a data-sharing/cost dialog, and require review before saving.
- Provider keys are session-only. The assistant bridge does not expose them.
- The assistant bridge is disabled by default, binds to loopback, authenticates with a rotating token, rejects browser origins and validates all writes.
- Code uses BSD-2-Clause. Bundled Wiki-derived datasets retain separate CC BY-NC-SA attribution and terms in `THIRD_PARTY_NOTICES.md`.
- Only plugin classes and data belong in the Hub artifact. The `-all.jar`, Windows launcher, previews, runtime copies, snapshots and account files are development/support material.
- Local test result: 104 passing tests against RuneLite 1.13.3. An actual Plugin Hub CI/reviewer result is still required; approval is not guaranteed.

## Before submission

1. Review the public source, author name, license and screenshots.
2. Publish the source repository and confirm the build workflow passes.
3. Pin the published commit in the Hub marker and open one submission pull request.
4. Respond to CI or reviewer requests on that same pull request.

Official process: https://github.com/runelite/plugin-hub#submitting-a-plugin

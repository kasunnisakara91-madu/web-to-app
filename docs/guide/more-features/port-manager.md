# Port Manager

The Port Manager is the built-in coordinator for the local-site ports used by your generated apps — it has no screen of its own and works silently in the background. Multi-Web apps can embed packaged apps (HTML, Frontend, Gallery) as sites, and those sites are served over loopback ports inside the app; the Port Manager allocates those ports so they don't collide.

## Conflict policy

When a local site needs a port that's taken, the configured policy applies:

- `AUTO_KILL` — stop the conflicting service.
- `ALERT` — notify and let you decide.

(`REASSIGN` — picking another port — is the engine's internal fallback for zero/unconfigured ports, not a user-selectable policy.)

## Notes

Local sites allocate through the Port Manager and release their port on stop, so ports don't leak between apps — or between WebToApp-built apps installed side by side.

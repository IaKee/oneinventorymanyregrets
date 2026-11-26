# Client, UI, audio, and multiplayer manual test checklist

Complete this checklist against the release-candidate JAR, not development classes. Record OS, GPU/driver, Java, Minecraft, Fabric Loader/API, mod version/commit, date, tester, screenshots/video, logs, and result (`PASS`, `FAIL`, `BLOCKED`) for every run.

## Test matrix

- [ ] Windows, normal display, modded client + integrated server.
- [ ] Linux under X11/Wayland where available, modded client + integrated server.
- [ ] Dedicated server with two modded clients.
- [ ] Dedicated server with one modded client and one vanilla client, if the server accepts the connection.
- [ ] GUI scale Auto and at least one fixed scale at 640×360, 1280×720, and native resolution.
- [ ] Fresh config and upgraded config/save from the previous public candidate.

## Settings screen

- [ ] Open from the title-screen Options menu; title, sections, labels, tooltips, reset, and Done render without clipping.
- [ ] At 640×360, scroll through every option; footer buttons stay reachable and no control leaves the viewport.
- [ ] Navigate without a mouse using Tab, Shift+Tab, Enter/Space, arrows, and Escape; focus order is logical and visible.
- [ ] Enable the narrator and traverse every control; label, value, tooltip/context, and disabled state are understandable.
- [ ] In singleplayer as host, change each setting, close/reopen the screen, and confirm persistence and immediate behavior.
- [ ] Join a remote multiplayer server as a non-host; server-owned controls must be blocked/read-only and must not change local/server config.
- [ ] Toggle `syncHotbar`; confirm the main-hand control becomes coupled/disabled when expected and re-enables correctly.
- [ ] Toggle creative-death exclusion; confirm the dependent wipe option follows its enabled state.
- [ ] Toggle shared mob sounds and each filter; confirm volume/filter controls expose the correct enabled state.
- [ ] Reset to defaults, confirm the displayed values and persisted properties, then restart client/server and recheck.
- [ ] Force a config save/reload failure in a disposable environment; confirm a visible failure message and no silent partial transition.

## HUD and TAB overlay

- [ ] With two linked players, cause melee, projectile, fire, lava, fall, drowning, magic, explosion, starvation, and void damage; verify icon, name, amount, ordering, fade, and five-entry cap.
- [ ] Eat food and lose breath from the remote player; verify right-side indicators, stacking, pause behavior, and no stale entries after disconnect/world change.
- [ ] Open TAB at affinity 0%, partial, 100%, and above-100 hunger weight; verify values, colors, icons, alignment, spectator rows, and long player names.
- [ ] Repeat at every target resolution/GUI scale and with chat, boss bar, subtitles, status effects, and scoreboards visible; check overlap.
- [ ] Disconnect/reconnect and switch servers; no bond/HUD state from the previous connection may remain.

## Underwater and shared mob audio

- [ ] With `underwaterMuffle` on, submerge only the teammate; the linked client receives underwater ambience/fog behavior, then returns cleanly when nobody is submerged.
- [ ] Repeat while local player is underwater, dead, spectator, disconnected, and on an unmodded server; shared state must not leak into invalid contexts.
- [ ] Toggle `underwaterMuffle` off during active ambience; loop/sub-sounds stop without hanging or duplicating.
- [ ] For `sharedMobSounds`, test `all`, `hostile`, `neutral`, and `none` with mobs near the remote player.
- [ ] Put players in same and different dimensions; verify configured direction, replicated distance, volume, and affinity scaling using headphones plus subtitles.
- [ ] Generate simultaneous and rapid repeated mob sounds; verify no echo storm, clipping, duplication, or sound retained after reconnect.

## Real multiplayer and compatibility

- [ ] Start a dedicated server from a clean directory, join two modded clients, create/switch/delete teams, reconnect both, restart server, and verify hive state.
- [ ] Repeat reconnect during item use, death/respawn, portal travel, underwater state, and active effects.
- [ ] Attempt a vanilla-client connection. Record whether it connects, what features degrade, server/client logs, and whether modded peers remain stable; do not claim support from code inspection alone.
- [ ] Attempt a modded client against a vanilla server; verify connection behavior and that client-only overlays/audio do not activate from stale state.
- [ ] Exercise operator, non-operator, integrated host, and dedicated console setting commands; verify authorization and translated feedback.
- [ ] Inspect server and both client logs for mixin failures, rejected payloads, decoder errors, resource warnings, and uncaught exceptions.

## Exit criteria

- [ ] Automated `test`, server GameTests, Client GameTests, and translation validation are green on the same commit.
- [ ] Every manual row has evidence and no unresolved P0/P1 failure.
- [ ] Vanilla-client behavior is described exactly as observed, including unsupported outcomes.
- [ ] Known visual/audio differences are documented in release notes before sign-off.

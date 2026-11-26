# Graphic asset provenance register

This register records repository evidence without treating commit authorship as proof of artistic authorship. The project rights holder must replace each `confirmation required` status with an explicit origin statement before public release.

| Asset group | Paths | First-known / relevant commit | Git author | Status |
| --- | --- | --- | --- | --- |
| Mod icon | `src/main/resources/assets/oneinventorymanyregrets/icon.png` | `b822097` (`initial commit`, 12 Jul 2025) | IaKee / Giordano Souza | confirmation required: original work, commissioned work, or third-party source/license |
| TAB sprites | `textures/gui/sprites/tab/*.png` | `f6c06cc` (`add affinity scaling...`, 24 Nov 2026) | Giordano Souza | confirmation required |
| HUD damage sprites | `textures/gui/sprites/hud/damage/*.png` | `4cbbc6b` (`add shared teleport...`, 25 Nov 2026) | Giordano Souza | confirmation required |
| Partial-effect icons | `textures/mob_effect/*.png` | `9d9807a` (`add partial-affinity effects...`, 25 Nov 2026) | Giordano Souza | confirmation required |

For each group, record one of:

- `original work by <name>, created <date/tool>, released under project MIT license`;
- `commissioned from <name>, rights assignment/license at <reference>`;
- `derived from <source URL or package>, <license>, modifications <summary>`.

Do not infer that small dimensions, lack of embedded metadata, or presence in a maintainer-authored commit establishes ownership.

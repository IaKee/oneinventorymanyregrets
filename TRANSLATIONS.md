# Translation policy

## Supported languages

Only these languages are reviewed, supported, and included in release JARs:

- `en_us` — English (United States), the reference key set;
- `pt_br` — Portuguese (Brazil).

Every other JSON under `src/main/resources/assets/oneinventorymanyregrets/lang/` is an **experimental AI-generated draft**. Those drafts have not received guaranteed human review, have no confirmed translator provenance, and are excluded from release artifacts by `processResources` in `build.gradle`. Their presence in the source tree is not a claim of support or a grant of rights beyond any rights that their respective contributors can establish.

## Automated validation

Run:

```text
node scripts/validate-translations.mjs
```

The default CI policy requires all language files to be valid JSON with string values, checks placeholders on every translated key, and requires `en_us` and `pt_br` to have exactly the same keys. Experimental drafts may be incomplete and produce a warning. To require every draft to be complete during a translation campaign, run:

```text
node scripts/validate-translations.mjs --strict-all
```

Placeholder order may change through positional forms (`%1$s`, `%2$s`), but the index and conversion type must remain compatible with the English reference.

## Experimental backlog

As of 26 November 2026, each of the 143 experimental drafts has 149 of the 194 reference keys. The missing set is:

- settings introduced or completed by the item-integrity and hazard work:
  - `oimr.settings.sync_effects`
  - `oimr.settings.sync_effects.tooltip`
  - `oimr.settings.random_death_loot`
  - `oimr.settings.random_death_loot.tooltip`
  - `oimr.settings.scaling_mode.1x`
  - `oimr.settings.scaling_mode.nx`
  - `oimr.settings.lava_damage_mode`
  - `oimr.settings.lava_damage_mode.tooltip`
  - `oimr.settings.underwater_breath_mode`
  - `oimr.settings.underwater_breath_mode.tooltip`
  - `oimr.settings.dot_damage_mode`
  - `oimr.settings.dot_damage_mode.tooltip`
- partial-affinity effects from BL-004:
  - `effect.oneinventorymanyregrets.translucency`
  - `effect.oneinventorymanyregrets.twilight_vision`
  - `effect.oneinventorymanyregrets.aquatic_respiration`
  - `effect.oneinventorymanyregrets.cushioned_fall`
  - `effect.oneinventorymanyregrets.faint_glow`
  - `effect.oneinventorymanyregrets.ember_ward`
  - `effect.oneinventorymanyregrets.partial_levitation`
  - `effect.oneinventorymanyregrets.fluid_grace`
  - `effect.oneinventorymanyregrets.conduit_echo`
  - `effect.oneinventorymanyregrets.nautilus_echo`
  - `effect.oneinventorymanyregrets.fast_walking`
  - `effect.oneinventorymanyregrets.heavy_step`
  - `effect.oneinventorymanyregrets.nimble_hands`
  - `effect.oneinventorymanyregrets.weary_hands`
  - `effect.oneinventorymanyregrets.might_surge`
  - `effect.oneinventorymanyregrets.feeble_grip`
  - `effect.oneinventorymanyregrets.spring_step`
  - `effect.oneinventorymanyregrets.warding_aura`
  - `effect.oneinventorymanyregrets.minor_fortune`
  - `effect.oneinventorymanyregrets.minor_jinx`
- shared-audio settings:
  - `oimr.settings.shared_mob_sound_filter`
  - `oimr.settings.shared_mob_sound_filter.tooltip`
  - `oimr.settings.shared_mob_sound_filter.all`
  - `oimr.settings.shared_mob_sound_filter.hostile`
  - `oimr.settings.shared_mob_sound_filter.neutral`
  - `oimr.settings.shared_mob_sound_filter.none`
  - `oimr.settings.shared_mob_sound_volume`
  - `oimr.settings.shared_mob_sound_volume.tooltip`
- operational messages:
  - `oimr.command.settings_reload_failed`
  - `oimr.command.settings_reload_applied_not_persisted`
  - `oimr.command.settings_save_failed`
  - `oimr.command.cleanup.dry_run`
  - `oimr.command.cleanup.executed`

The validator is the authoritative source when the English file changes; this dated list documents the current review queue rather than replacing the automated comparison.

## Contributing a translation

1. Start from the current `en_us.json`, not another experimental draft.
2. Translate every key and preserve placeholder semantics.
3. Run the validator with `--strict-all` (or validate the submitted file against `en_us`).
4. In the pull request, state the translator, whether machine translation was used, the reviewer's language proficiency, and the license/permission for the contribution.
5. A language becomes supported and packaged only after a maintainer records human review and adds it to both `supportedFiles` in the validator and the allow-list in `build.gradle`.

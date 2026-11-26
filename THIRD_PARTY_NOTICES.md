# Third-party notices and license scope

The repository-level `LICENSE` applies to original One Inventory, Many Regrets material for which Giordano Souza (`iakee`) holds the rights. It does not relicense third-party software, Mojang/Minecraft content, or material whose provenance has not been established.

## Distributed third-party material

| Component | Repository paths | License / notice | Distribution note |
| --- | --- | --- | --- |
| Gradle Wrapper | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` | Apache License 2.0; retain the upstream file headers and notices | Build tooling, not bundled inside the mod JAR |

Fabric Loader, Fabric API, Fabric Loom, Gradle, Java, and Minecraft are dependencies or build tools owned by their respective projects and are not relicensed by this project. Their artifacts retain their own licenses and notices.

## Experimental translations

The 143 language files other than `en_us.json` and `pt_br.json` are AI-generated drafts without confirmed human translator provenance. They are retained for review in the source repository, are excluded from the release JAR, and are not represented as MIT-licensed project originals. See `TRANSLATIONS.md`.

## Graphic assets

The repository history attributes the checked-in icon and sprites to commits authored by Giordano Souza, but authorship/source confirmation is not derivable from Git metadata alone. Their inventory and first-known repository commits are recorded in `ASSET_PROVENANCE.md`. A public release must not make a stronger ownership claim until the rights holder completes that declaration; assets derived from third-party work must instead name the source and compatible license here.

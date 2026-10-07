# Launch PokeDaisy from ES-DE

[ES-DE](https://es-de.org) can open a game straight in PokeDaisy, with no library
screen in between. Leaving the game (hold BACK, or the exit hotkey) takes you back
to ES-DE. This guide adds PokeDaisy as an emulator for GBA in ES-DE's custom
systems folder, so it shows up next to the emulators you already use.

Not yet tried from a real ES-DE. If something doesn't work,
[open an issue](https://github.com/lidor30/pokedaisy/issues).

## 1. Tell ES-DE where PokeDaisy is

In `ES-DE/custom_systems/`, add a find rule to `es_find_rules.xml` (create the file
if it isn't there):

```xml
<ruleList>
    <emulator name="POKEDAISY">
        <rule type="androidpackage">
            <entry>com.pokedaisy.app/.LaunchActivity</entry>
        </rule>
    </emulator>
</ruleList>
```

## 2. Add a launch command for GBA

In `ES-DE/custom_systems/`, put a copy of ES-DE's bundled `gba` system entry in
`es_systems.xml` and add this line to its commands:

```xml
<command label="PokeDaisy (Standalone)">%EMULATOR_POKEDAISY% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%</command>
```

Then pick **PokeDaisy (Standalone)** as the alternative emulator for the GBA system,
or for single games from their menu.

## What PokeDaisy does with the ROM

See [Launch from a frontend](../DEVELOPMENT.md#launch-from-a-frontend-cocoon-iisu-es-de-)
for the full list. In short: a ROM it can read is played where it is, anything else
is copied into PokeDaisy's library once. Saves are found by the ROM's file name, so
point PokeDaisy's Settings > Folders at your other emulator's saves folder (RetroArch's,
for example) to share them.

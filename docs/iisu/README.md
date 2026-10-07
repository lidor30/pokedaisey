# Launch PokeDaisy from iiSU

[iiSU](https://github.com/iisu-network/iiSU) doesn't list PokeDaisy as an emulator
yet, but its emulator list is a JSON file you can import. This guide adds PokeDaisy
to that list and then picks it for the games you want, say FireRed, while every
other GBA game keeps the emulator it uses now.

Checked with iiSU 0.0.7.3 on the AYN Thor. Menu names can differ a little
between iiSU versions.

## 1. Add PokeDaisy to iiSU's emulator list (once)

### The quick way: import ready-made files

1. Download both files to your device (on each file's page, use **Download raw file**):
   - [emuladores.json](emuladores.json): iiSU's emulator list (version 0.0.11)
     with PokeDaisy added to the GBA console. Nothing else is changed.
   - [supported_emulators.json](supported_emulators.json): iiSU's list of known
     emulator apps (version 0.0.5) with PokeDaisy added.
2. In iiSU, open Settings (Select) > iiSU Settings > Updates, then:
   - **Import emuladores.json** and pick the downloaded `emuladores.json`.
   - **Import supported_emulators.json** and pick the downloaded
     `supported_emulators.json`.

> [!WARNING]
> Importing replaces iiSU's whole list, for every console, not only GBA. If you
> changed iiSU's emulator list yourself (your own commands, or a custom
> `emuladores.json` you imported before), those changes are lost. Also, if your
> iiSU has a newer list than 0.0.11, this file takes it back to 0.0.11. In either
> case, add PokeDaisy to your own file instead (below).

### Or add it to your own list

1. Find iiSU's current list: `emuladores.json` in the `iiSULauncher/Emuladores/`
   folder of iiSU's storage location (for example
   `/storage/XXXX-XXXX/iiSU/iiSULauncher/Emuladores/` on an SD card), next to
   `emuladores.version.txt`, which holds its version. Copy it somewhere you can
   edit it, like `Download/`.

2. Open the copy in a text editor and find the GBA console, the entry with
   `"shortName": "gba"`. Add this at the end of its `"emulators": [ ... ]` list,
   after the last emulator's closing `}` (put a comma after that `}`):

   ```json
   {
     "id": "POKEDAISY",
     "name": "PokeDaisy (Standalone)",
     "routeType": "uri",
     "commands": [
       {
         "description": "PokeDaisy",
         "command": "com.pokedaisy.app/.LaunchActivity -a android.intent.action.VIEW -d %ROM_URI%"
       },
       {
         "description": "PokeDaisy Path",
         "command": "com.pokedaisy.app/.LaunchActivity -e rom %ROM_PATH%"
       }
     ],
     "packages": ["com.pokedaisy.app"]
   }
   ```

   <details>
   <summary>Or let a script do it from a computer (adb + Python 3)</summary>

   Set `SRC` to iiSU's folder on your device, then:

   ```bash
   SRC=/storage/XXXX-XXXX/iiSU/iiSULauncher/Emuladores
   adb pull "$SRC/emuladores.json" .
   python3 - <<'EOF'
   import json
   d = json.load(open("emuladores.json"))
   gba = next(c for c in d["consoles"] if c["shortName"] == "gba")
   gba["emulators"] = [e for e in gba["emulators"] if e["id"] != "POKEDAISY"] + [{
       "id": "POKEDAISY", "name": "PokeDaisy (Standalone)", "routeType": "uri",
       "commands": [
           {"description": "PokeDaisy",
            "command": "com.pokedaisy.app/.LaunchActivity -a android.intent.action.VIEW -d %ROM_URI%"},
           {"description": "PokeDaisy Path",
            "command": "com.pokedaisy.app/.LaunchActivity -e rom %ROM_PATH%"}],
       "packages": ["com.pokedaisy.app"]}]
   json.dump(d, open("emuladores.json", "w"), indent=2, ensure_ascii=False)
   EOF
   adb push emuladores.json /sdcard/Download/
   ```

   </details>

3. In iiSU, open Settings (Select) > iiSU Settings > Updates > **Import
   emuladores.json** and pick your edited file. To do the same for
   `supported_emulators.json` (from the same folder), add
   `{"name": "PokeDaisy", "packages": ["com.pokedaisy.app"]}` to the end of its
   list.

Leave the GBA console's own emulator as it is: the next step sets PokeDaisy for
single games only.

## 2. Open a game in PokeDaisy

For each game you want in PokeDaisy:

1. Focus the game and press the minus button (`−`) to open its details.
2. Select **Settings**, then **Launch options**.
3. Choose **Override emulator** and pick **PokeDaisy (Standalone)**.

That game now opens straight in PokeDaisy. Leaving the game (hold BACK) takes you
back to iiSU. Other games in the GBA folder still open in the console's emulator.

## If something goes wrong

- **The game doesn't start**: in the game's **Launch options**, switch its
  command to **PokeDaisy Path**.
  It hands PokeDaisy the file's path instead of a link, which needs PokeDaisy's
  All files access (it asks for it the first time).
- **PokeDaisy is gone from the list**: iiSU replaces its emulator list when it
  downloads an update. Add PokeDaisy to the new list ("Or add it to your own
  list" above): the ready-made files would take it back to the older version.
  The games you set to PokeDaisy may need step 2 again too.
- **The bottom screen says the game isn't supported**: the game plays, but the
  companion only works with the games in the main README's
  [supported games](../../README.md#supported-games) list. Keep other games on
  your usual emulator.
- **No save**: PokeDaisy finds a save by the ROM's file name in its saves folder.
  Point PokeDaisy's Settings > Folders at the folder your other emulator saves to
  (RetroArch's, for example) to share saves.

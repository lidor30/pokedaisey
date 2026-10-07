captured: 2026-10-07 (headless mgba_dump, by hand)
rom: Pokemon SoulGold v1.2 (~/Downloads/Game ROMs & Emulation/gba/Soulgold (v1.2).gba)
save: ~/Documents/RetroArch/saves/mGBA/Soulgold (v1.2).sav
boot: as the soulgold_v12 fixture, then two copies of the Cyndaquil poked into party slots 2-3
  (96 bytes apart from 0x02039024, HP 17 and 13; gPlayerPartyCount 0x020394D8 = 3), and a
  scripted wild battle: B6 1900 05 0000 0000 0000 00 0000 0000 B7 02 (setwildbattle
  SPECIES_PIKACHU, 5; dowildbattle; end) poked at 0x0203FF00 and `call 0x08236D5C
  0x0203FF00` (ScriptContext_SetupScript). At the action menu: A (move menu: controller
  0x08060E59), B, RIGHT, A (bag: 0x0805FB01), B, then POKéMON (party menu: 0x0805FB45,
  gPartyMenu 0x02038D2C slotId 0 -> 2), DOWN, DOWN, A, A (SHIFT) - the third mon (13/21)
  comes in - A through the messages to the action menu again (0x08061D09): dumped.

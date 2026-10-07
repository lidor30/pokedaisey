package com.pokedaisy.app.companion.data

// Pokémon Odyssey v4.1.1's added species. It keeps vanilla FireRed's
// gSpeciesNames (0x08245EE0, 11-byte names) and writes its 25 additions into
// the slots vanilla leaves unused (252-276, between CELEBI and TREECKO) - read
// straight from the ROM. Merged over the shared table (see ActiveTables).
val speciesNamesOdyssey: Map<Int, String> = mapOf(
    252 to "Roserade",
    253 to "Ambipom",
    254 to "Mismagius",
    255 to "Honchkrow",
    256 to "Weavile",
    257 to "Magnezone",
    258 to "Lickilicky",
    259 to "Rhyperior",
    260 to "Tangrowth",
    261 to "Electivire",
    262 to "Magmortar",
    263 to "Togekiss",
    264 to "Yanmega",
    265 to "Froslass",
    266 to "Gliscor",
    267 to "Porygon-Z",
    268 to "Gallade",
    269 to "Probopass",
    270 to "Dusknoir",
    271 to "Mamoswine",
    272 to "Farigiraf",
    273 to "Leafeon",
    274 to "Glaceon",
    275 to "Abyss Eye",
    276 to "Tentacle",
)

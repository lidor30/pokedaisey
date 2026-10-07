package com.pokedaisy.app.companion.i18n

/** The companion's game tabs: battle panes, party / summary, bag, map, states, card, and the tab frame. */
// Also used by other areas (DEX, SETTINGS), defined only here: HP, ATTACK, DEFENSE, SP. ATK, SP. DEF,
// SPEED, TOTAL, ALL, ITEMS, KEY ITEMS, LOADING, SAVE STATES, SAVE, UNDO SAVE, UNDO LOAD, NO GAME RUNNING.
internal val trCompanion: Map<String, Tr> = mapOf(
    // Tab frame: unsupported ROM, loading, errors.
    "THIS ROM ISN'T SUPPORTED" to Tr(ja = "この ROMには たいおう していません", fr = "CETTE ROM N'EST PAS PRISE EN CHARGE", de = "DIESE ROM WIRD NICHT UNTERSTÜTZT", it = "QUESTA ROM NON È SUPPORTATA", es = "ESTA ROM NO ES COMPATIBLE"),
    "The game plays normally on the top screen, but the second screen can't read its party, map, items or battles." to Tr(ja = "ゲームは うえの がめんで ふつうに あそべますが 2つめの がめんでは てもち マップ どうぐ バトルを よみとれません。", fr = "Le jeu fonctionne normalement sur l'écran du haut, mais le second écran ne peut pas lire son équipe, sa carte, ses objets ni ses combats.", de = "Das Spiel läuft oben ganz normal, aber der zweite Bildschirm kann Team, Karte, Items und Kämpfe nicht lesen.", it = "Il gioco funziona normalmente sullo schermo superiore, ma il secondo schermo non può leggerne squadra, mappa, strumenti o lotte.", es = "El juego funciona con normalidad en la pantalla superior, pero la segunda pantalla no puede leer su equipo, mapa, objetos ni combates."),
    "SETTINGS (the gear below) still works." to Tr(ja = "せってい (したの はぐるま) は つかえます。", fr = "OPTIONS (l'engrenage en bas) fonctionne toujours.", de = "OPTIONEN (das Zahnrad unten) funktioniert weiterhin.", it = "OPZIONI (l'ingranaggio in basso) funziona ancora.", es = "AJUSTES (el engranaje de abajo) sigue funcionando."),
    "no data yet" to Tr(ja = "まだ データが ありません", fr = "pas encore de données", de = "noch keine Daten", it = "ancora nessun dato", es = "aún no hay datos"),
    "starting…" to Tr(ja = "じゅんびちゅう…", fr = "démarrage…", de = "startet…", it = "avvio…", es = "iniciando…"),

    // The side panel's tab on a single-screen device (SidePanelHandle).
    "Lock the companion beside the game" to Tr(ja = "コンパニオンを ゲームの よこに こてい", fr = "Fixer le compagnon à côté du jeu", de = "Begleiter neben dem Spiel fixieren", it = "Fissa il compagno accanto al gioco", es = "Fijar el compañero junto al juego"),
    "Open the companion" to Tr(ja = "コンパニオンを ひらく", fr = "Ouvrir le compagnon", de = "Begleiter öffnen", it = "Apri il compagno", es = "Abrir el compañero"),
    "Close the companion" to Tr(ja = "コンパニオンを とじる", fr = "Fermer le compagnon", de = "Begleiter schließen", it = "Chiudi il compagno", es = "Cerrar el compañero"),
    "Unlock the companion" to Tr(ja = "コンパニオンの こていを かいじょ", fr = "Libérer le compagnon", de = "Begleiter lösen", it = "Sblocca il compagno", es = "Soltar el compañero"),

    // Battle sides and the foe's state (foeHeading's halves, BattlerCard).
    "YOU" to Tr(ja = "じぶん", fr = "TOI", de = "DU", it = "TU", es = "TÚ"),
    "FOE" to Tr(ja = "あいて", fr = "ENNEMI", de = "GEGNER", it = "NEMICO", es = "ENEMIGO"),
    "NEXT" to Tr(ja = "つぎ", fr = "SUIVANT", de = "NÄCHSTES", it = "PROSSIMO", es = "PRÓXIMO"),
    "FAINTED" to Tr(ja = "ひんし", fr = "K.O.", de = "BESIEGT", it = "ESAUSTO", es = "DEBILITADO"),
    "RESERVE" to Tr(ja = "ひかえ", fr = "RÉSERVE", de = "RESERVE", it = "RISERVA", es = "RESERVA"),
    "UNSEEN" to Tr(ja = "みかくにん", fr = "INCONNU", de = "UNBEKANNT", it = "IGNOTO", es = "OCULTO"),
    "OUT" to Tr(ja = "でている", fr = "EN JEU", de = "AKTIV", it = "ATTIVO", es = "ACTIVO"),
    "BEST" to Tr(ja = "いちおし", fr = "TOP", de = "TOP", it = "TOP", es = "MEJOR"),
    "1ST" to Tr(ja = "1ばん", fr = "1ER", de = "1.", it = "1°", es = "1.º"),
    "2ND" to Tr(ja = "2ばん", fr = "2E", de = "2.", it = "2°", es = "2.º"),
    "3RD" to Tr(ja = "3ばん", fr = "3E", de = "3.", it = "3°", es = "3.º"),
    "VS" to Tr(ja = "VS", fr = "VS", de = "VS", it = "VS", es = "VS"),
    "Unknown foe Pokémon" to Tr(ja = "わからない あいての ポケモン", fr = "Pokémon ennemi inconnu", de = "Unbekanntes gegnerisches Pokémon", it = "Pokémon nemico sconosciuto", es = "Pokémon enemigo desconocido"),
    "{0} Lv {1}" to Tr(ja = "{0} Lv {1}", fr = "{0} N. {1}", de = "{0} Lv {1}", it = "{0} Lv {1}", es = "{0} Nv {1}"),

    // Battle panes: buttons, the controls' states.
    "SUGGESTIONS" to Tr(ja = "おすすめ", fr = "CONSEILS", de = "TIPPS", it = "CONSIGLI", es = "CONSEJOS"),
    "STATS" to Tr(ja = "のうりょく", fr = "STATS", de = "WERTE", it = "STATS", es = "STATS"),
    "MOVES" to Tr(ja = "わざ", fr = "CAPACITÉS", de = "ATTACKEN", it = "MOSSE", es = "MOVIMIENTOS"),
    "FIGHT" to Tr(ja = "たたかう", fr = "ATTAQUE", de = "KAMPF", it = "LOTTA", es = "LUCHA"),
    "RUN" to Tr(ja = "にげる", fr = "FUITE", de = "FLUCHT", it = "FUGA", es = "HUIR"),
    "Choose a POKéMON." to Tr(ja = "ポケモンを えらんで ください", fr = "Choisis un POKéMON.", de = "Wähle ein POKéMON.", it = "Scegli un POKéMON.", es = "Elige un POKéMON."),
    "Confirming target…" to Tr(ja = "あいてを けってい ちゅう…", fr = "Choix de la cible…", de = "Ziel wird bestätigt…", it = "Conferma del bersaglio…", es = "Confirmando objetivo…"),
    "Waiting for your turn…" to Tr(ja = "じぶんの ばんを まっています…", fr = "En attente de ton tour…", de = "Warte auf deinen Zug…", it = "In attesa del tuo turno…", es = "Esperando tu turno…"),
    "NO FOE ON THE FIELD" to Tr(ja = "あいての ポケモンが いません", fr = "AUCUN ENNEMI SUR LE TERRAIN", de = "KEIN GEGNER IM KAMPF", it = "NESSUN NEMICO IN CAMPO", es = "NINGÚN ENEMIGO EN CAMPO"),
    "NO USABLE DAMAGING MOVES IN YOUR PARTY" to Tr(ja = "てもちに つかえる こうげきわざが ありません", fr = "AUCUNE CAPACITÉ OFFENSIVE UTILISABLE DANS TON ÉQUIPE", de = "KEINE NUTZBARE SCHADENSATTACKE IM TEAM", it = "NESSUNA MOSSA OFFENSIVA USABILE IN SQUADRA", es = "NINGÚN MOVIMIENTO OFENSIVO USABLE EN TU EQUIPO"),

    // Moves and their verdicts ({0} = the multiplier, e.g. 2x).
    "{0}'S MOVES" to Tr(ja = "{0}の わざ", fr = "CAPACITÉS DE {0}", de = "ATTACKEN VON {0}", it = "MOSSE DI {0}", es = "MOVIMIENTOS DE {0}"),
    "NO MOVE DATA" to Tr(ja = "わざの データなし", fr = "AUCUNE DONNÉE DE CAPACITÉ", de = "KEINE ATTACKENDATEN", it = "NESSUN DATO MOSSE", es = "SIN DATOS DE MOVIMIENTOS"),
    "PWR" to Tr(ja = "いりょく", fr = "PUIS.", de = "STÄRKE", it = "POT.", es = "POT."),
    "PP" to Tr(ja = "PP", fr = "PP", de = "AP", it = "PP", es = "PP"),
    "PWR {0}" to Tr(ja = "いりょく {0}", fr = "PUIS. {0}", de = "STÄRKE {0}", it = "POT. {0}", es = "POT. {0}"),
    "PP {0}" to Tr(ja = "PP {0}", fr = "PP {0}", de = "AP {0}", it = "PP {0}", es = "PP {0}"),
    "NO PP" to Tr(ja = "PPなし", fr = "SANS PP", de = "KEINE AP", it = "SENZA PP", es = "SIN PP"),
    "STATUS" to Tr(ja = "へんか", fr = "STATUT", de = "STATUS", it = "STATO", es = "ESTADO"),
    "SUPER {0}" to Tr(ja = "ばつぐん {0}", fr = "SUPER {0}", de = "SEHR EFF. {0}", it = "SUPER {0}", es = "MUY EF. {0}"),
    "RESISTED {0}" to Tr(ja = "いまひとつ {0}", fr = "PEU EFF. {0}", de = "WENIG EFF. {0}", it = "POCO EFF. {0}", es = "POCO EF. {0}"),
    "NEUTRAL {0}" to Tr(ja = "ふつう {0}", fr = "NEUTRE {0}", de = "NEUTRAL {0}", it = "NEUTRO {0}", es = "NEUTRO {0}"),
    "IMMUNE {0}" to Tr(ja = "こうかなし {0}", fr = "IMMUNISÉ {0}", de = "IMMUN {0}", it = "IMMUNE {0}", es = "INMUNE {0}"),

    // Type matchups.
    "{0} IS" to Tr(ja = "{0}の あいしょう", fr = "{0} EST", de = "{0} IST", it = "{0} È", es = "{0} ES"),
    "WEAK TO" to Tr(ja = "じゃくてん", fr = "FAIBLE CONTRE", de = "SCHWACH GEGEN", it = "DEBOLE CONTRO", es = "DÉBIL CONTRA"),
    "RESISTS" to Tr(ja = "たいせい", fr = "RÉSISTE", de = "RESISTENT", it = "RESISTE", es = "RESISTE"),
    "IMMUNE TO" to Tr(ja = "むこう", fr = "IMMUNISÉ CONTRE", de = "IMMUN GEGEN", it = "IMMUNE A", es = "INMUNE A"),
    "NO MATCHUP DATA" to Tr(ja = "あいしょうの データなし", fr = "PAS DE DONNÉES DE TYPES", de = "KEINE TYPDATEN", it = "NESSUN DATO SUI TIPI", es = "SIN DATOS DE TIPOS"),

    // Summary: level, bars, the STATS page (the games' own summary words).
    "Lv" to Tr(ja = "Lv", fr = "N.", de = "Lv", it = "Lv", es = "Nv"),
    "Lv{0}" to Tr(ja = "Lv{0}", fr = "N.{0}", de = "Lv{0}", it = "Lv{0}", es = "Nv{0}"),
    "EXP" to Tr(ja = "けいけん", fr = "EXP", de = "EP", it = "ESP", es = "EXP"),
    "MAX" to Tr(ja = "MAX", fr = "MAX", de = "MAX", it = "MAX", es = "MÁX"),
    "NEXT {0}" to Tr(ja = "つぎ {0}", fr = "SUIV. {0}", de = "NOCH {0}", it = "PROSS. {0}", es = "SIG. {0}"),
    "STAT" to Tr(ja = "のうりょく", fr = "STAT", de = "WERT", it = "STAT", es = "VALOR"),
    "IV" to Tr(ja = "こたいち", fr = "IV", de = "DV", it = "IV", es = "IV"),
    "EV" to Tr(ja = "どりょくち", fr = "EV", de = "FP", it = "EV", es = "EV"),
    "NATURE" to Tr(ja = "せいかく", fr = "NATURE", de = "WESEN", it = "NATURA", es = "NATURALEZA"),
    "HIDDEN POWER" to Tr(ja = "めざめるパワー", fr = "PUIS. CACHÉE", de = "KRAFTRESERVE", it = "INTROFORZA", es = "POD. OCULTO"),
    "NO STATS FOR THIS ONE" to Tr(ja = "のうりょくの データなし", fr = "PAS DE STATS POUR CELUI-CI", de = "KEINE WERTE FÜR DIESES", it = "NESSUNA STAT. PER QUESTO", es = "SIN STATS PARA ESTE"),

    // Bag: pockets (the games' own names) and the sort button.
    "POKé BALLS" to Tr(ja = "ボール", fr = "POKé BALLS", de = "BÄLLE", it = "POKé BALL", es = "POKé BALLS"),
    "TMs & HMs" to Tr(ja = "わざマシン", fr = "CT & CS", de = "TM & VM", it = "MT & MN", es = "MT y MO"),
    "BERRIES" to Tr(ja = "きのみ", fr = "BAIES", de = "BEEREN", it = "BACCHE", es = "BAYAS"),
    "All" to Tr(ja = "すべて", fr = "Tout", de = "Alle", it = "Tutto", es = "Todo"),
    "Items" to Tr(ja = "どうぐ", fr = "Objets", de = "Items", it = "Strumenti", es = "Objetos"),
    "Poke Balls" to Tr(ja = "ボール", fr = "Poké Balls", de = "Bälle", it = "Poké Ball", es = "Poké Balls"),
    "Berries" to Tr(ja = "きのみ", fr = "Baies", de = "Beeren", it = "Bacche", es = "Bayas"),
    "Key Items" to Tr(ja = "たいせつなもの", fr = "Objets rares", de = "Basis-Items", it = "Strum. base", es = "Obj. clave"),
    "Default" to Tr(ja = "もとの じゅん", fr = "Défaut", de = "Standard", it = "Predefinito", es = "Original"),
    "Name" to Tr(ja = "なまえ", fr = "Nom", de = "Name", it = "Nome", es = "Nombre"),
    "Type" to Tr(ja = "しゅるい", fr = "Type", de = "Typ", it = "Tipo", es = "Tipo"),
    "Count" to Tr(ja = "かず", fr = "Quantité", de = "Anzahl", it = "Quantità", es = "Cantidad"),
    "No items" to Tr(ja = "どうぐが ありません", fr = "Aucun objet", de = "Keine Items", it = "Nessuno strumento", es = "No hay objetos"),
    "No items in this pocket" to Tr(ja = "この ポケットは からです", fr = "Aucun objet dans cette poche", de = "Keine Items in dieser Tasche", it = "Nessuno strumento in questa tasca", es = "No hay objetos en este bolsillo"),

    // Map.
    "MAP {0}" to Tr(ja = "マップ {0}", fr = "CARTE {0}", de = "KARTE {0}", it = "MAPPA {0}", es = "MAPA {0}"),
    "ME" to Tr(ja = "じぶん", fr = "MOI", de = "ICH", it = "IO", es = "YO"),
    "PLACES" to Tr(ja = "ばしょ", fr = "LIEUX", de = "ORTE", it = "LUOGHI", es = "LUGARES"),
    "YOUR LOCATION" to Tr(ja = "いまいる ばしょ", fr = "TA POSITION", de = "DEIN STANDORT", it = "LA TUA POSIZIONE", es = "TU UBICACIÓN"),
    "TOWNS & CITIES" to Tr(ja = "まち", fr = "VILLES", de = "STÄDTE", it = "CITTÀ", es = "PUEBLOS Y CIUDADES"),
    "ROUTES" to Tr(ja = "どうろ", fr = "ROUTES", de = "ROUTEN", it = "PERCORSI", es = "RUTAS"),
    "OTHER PLACES" to Tr(ja = "その ほか", fr = "AUTRES LIEUX", de = "ANDERE ORTE", it = "ALTRI LUOGHI", es = "OTROS LUGARES"),
    "({0}, {1}) facing {2}" to Tr(ja = "({0}, {1}) むき: {2}", fr = "({0}, {1}) direction : {2}", de = "({0}, {1}) Blick nach {2}", it = "({0}, {1}) verso {2}", es = "({0}, {1}) mirando al {2}"),
    "South" to Tr(ja = "みなみ", fr = "Sud", de = "Süden", it = "Sud", es = "Sur"),
    "North" to Tr(ja = "きた", fr = "Nord", de = "Norden", it = "Nord", es = "Norte"),
    "West" to Tr(ja = "にし", fr = "Ouest", de = "Westen", it = "Ovest", es = "Oeste"),
    "East" to Tr(ja = "ひがし", fr = "Est", de = "Osten", it = "Est", es = "Este"),
    "Southwest" to Tr(ja = "なんせい", fr = "Sud-ouest", de = "Südwesten", it = "Sud-ovest", es = "Suroeste"),
    "Southeast" to Tr(ja = "なんとう", fr = "Sud-est", de = "Südosten", it = "Sud-est", es = "Sureste"),
    "Northwest" to Tr(ja = "ほくせい", fr = "Nord-ouest", de = "Nordwesten", it = "Nord-ovest", es = "Noroeste"),
    "Northeast" to Tr(ja = "ほくとう", fr = "Nord-est", de = "Nordosten", it = "Nord-est", es = "Noreste"),
    "(no region map for this game yet)" to Tr(ja = "(この ゲームの マップは まだ ありません)", fr = "(pas encore de carte pour ce jeu)", de = "(noch keine Karte für dieses Spiel)", it = "(ancora nessuna mappa per questo gioco)", es = "(aún no hay mapa para este juego)"),
    "(no region map for this area)" to Tr(ja = "(この ばしょの マップは ありません)", fr = "(pas de carte pour cette zone)", de = "(keine Karte für dieses Gebiet)", it = "(nessuna mappa per quest'area)", es = "(no hay mapa para esta zona)"),

    // STATES tab.
    "NO GAME RUNNING" to Tr(ja = "ゲームが うごいていません", fr = "AUCUN JEU EN COURS", de = "KEIN SPIEL LÄUFT", it = "NESSUN GIOCO IN CORSO", es = "NINGÚN JUEGO EN CURSO"),
    "UNDO SAVE" to Tr(ja = "セーブを もどす", fr = "ANNULER SAUV.", de = "SICHERN RÜCKG.", it = "ANNULLA SALV.", es = "DESHACER GUARD."),
    "UNDO LOAD" to Tr(ja = "ロードを もどす", fr = "ANNULER CHARG.", de = "LADEN RÜCKG.", it = "ANNULLA CARIC.", es = "DESHACER CARGA"),
    "SLOT {0}" to Tr(ja = "スロット {0}", fr = "EMPL. {0}", de = "SLOT {0}", it = "SLOT {0}", es = "RANURA {0}"),
    "Slot {0}" to Tr(ja = "スロット {0}", fr = "Emplacement {0}", de = "Slot {0}", it = "Slot {0}", es = "Ranura {0}"),
    "EMPTY" to Tr(ja = "から", fr = "VIDE", de = "LEER", it = "VUOTO", es = "VACÍO"),

    // CARD tab.
    "TAP TO FLIP THE CARD" to Tr(ja = "タッチで うらがえす", fr = "TOUCHE POUR RETOURNER LA CARTE", de = "TIPPEN ZUM UMDREHEN", it = "TOCCA PER GIRARE LA SCHEDA", es = "TOCA PARA GIRAR LA FICHA"),
    "TAP TO SEE THE FRONT" to Tr(ja = "タッチで おもてを みる", fr = "TOUCHE POUR VOIR LE RECTO", de = "TIPPEN FÜR DIE VORDERSEITE", it = "TOCCA PER VEDERE IL FRONTE", es = "TOCA PARA VER EL FRENTE"),
)

#include "pd_tables.h"

#include <stdio.h>
#include <string.h>

// Upper-cases ASCII letters, leaving é (UTF-8 C3 A9) and the rest alone.
static const char* game_case(const char* src, char* buf, size_t len) {
    size_t i = 0;
    for (; src[i] && i + 1 < len; i++) {
        char c = src[i];
        buf[i] = (c >= 'a' && c <= 'z') ? (char) (c - 'a' + 'A') : c;
    }
    buf[i] = 0;
    return buf;
}

const char* pd_species_name(int species, char* buf, size_t len) {
    if (species == 0) return "-";
    if (species == PD_SPECIES_EGG) return "EGG";
    if (species > 0 && species < pd_species_names_count && pd_species_names[species]) {
        return game_case(pd_species_names[species], buf, len);
    }
    snprintf(buf, len, "#%d", species);
    return buf;
}

const char* pd_move_name(int move, char* buf, size_t len) {
    if (move <= 0) return "-";
    if (move < pd_moves_count && pd_moves[move].name) return game_case(pd_moves[move].name, buf, len);
    snprintf(buf, len, "MOVE#%d", move);
    return buf;
}

const char* pd_type_name(int type) {
    if (type >= 0 && type < pd_type_names_count && pd_type_names[type]) return pd_type_names[type];
    return "";
}

int pd_move_type(int move) {
    if (move > 0 && move < pd_moves_count && pd_moves[move].name) return pd_moves[move].type;
    return PD_TYPE_NONE;
}

int pd_move_power(int move) {
    if (move > 0 && move < pd_moves_count && pd_moves[move].name) return pd_moves[move].power;
    return 0;
}

static int single_pct(int atk, int def) {
    for (int i = 0; i < pd_type_chart_count; i++) {
        if (pd_type_chart[i].atk == atk && pd_type_chart[i].def == def) return pd_type_chart[i].pct;
    }
    return 100;
}

int pd_type_multiplier_pct(int atk, int def1, int def2) {
    int pct = single_pct(atk, def1);
    if (def2 != PD_TYPE_NONE && def2 != def1) pct = pct * single_pct(atk, def2) / 100;
    return pct;
}

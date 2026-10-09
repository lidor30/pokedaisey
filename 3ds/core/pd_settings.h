// The player's settings in a small text file (key=value lines), read at start
// and written on each change. Unknown keys and bad values fall back to the
// defaults, so an old or hand-edited file never breaks a start.
#ifndef PD_SETTINGS_H
#define PD_SETTINGS_H

#include <stdbool.h>

#include "pd_ui.h"

void pd_settings_defaults(struct pd_settings* s);
// Missing file = defaults (and false).
bool pd_settings_load(struct pd_settings* s, const char* path);
bool pd_settings_save(const struct pd_settings* s, const char* path);

#endif

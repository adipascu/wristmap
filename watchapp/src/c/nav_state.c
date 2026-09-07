#include "nav_state.h"

static NavState s_state;

const NavState *nav_state_get(void) {
  return &s_state;
}

static bool copy_text(char *dest, const Tuple *tuple) {
  if (!tuple) {
    return false;
  }
  if (strncmp(dest, tuple->value->cstring, NAV_TEXT_LEN) == 0) {
    return false;
  }
  strncpy(dest, tuple->value->cstring, NAV_TEXT_LEN - 1);
  dest[NAV_TEXT_LEN - 1] = '\0';
  return true;
}

static bool apply_active(DictionaryIterator *iter) {
  Tuple *tuple = dict_find(iter, KEY_NAV_ACTIVE);
  if (!tuple) {
    return false;
  }
  bool active = tuple->value->uint8 != 0;
  if (!active) {
    s_state.has_arrow = false;
    s_state.distance[0] = '\0';
    s_state.street[0] = '\0';
    s_state.instruction[0] = '\0';
    s_state.keep_lit = false;
  }
  if (active == s_state.active) {
    return false;
  }
  s_state.active = active;
  return true;
}

static bool apply_maneuver(DictionaryIterator *iter) {
  Tuple *tuple = dict_find(iter, KEY_MANEUVER);
  if (!tuple || tuple->value->uint8 == s_state.maneuver) {
    return false;
  }
  s_state.maneuver = (Maneuver)tuple->value->uint8;
  return true;
}

static bool apply_arrow(DictionaryIterator *iter) {
  Tuple *tuple = dict_find(iter, KEY_ARROW_BITMAP);
  if (!tuple || tuple->length < ARROW_BYTES) {
    return false;
  }
  if (s_state.has_arrow && memcmp(s_state.arrow, tuple->value->data, ARROW_BYTES) == 0) {
    return false;
  }
  memcpy(s_state.arrow, tuple->value->data, ARROW_BYTES);
  s_state.has_arrow = true;
  return true;
}

static bool apply_keep_lit(DictionaryIterator *iter) {
  Tuple *tuple = dict_find(iter, KEY_KEEP_LIT);
  if (!tuple) {
    return false;
  }
  bool keep_lit = tuple->value->uint8 != 0 && s_state.active;
  if (keep_lit == s_state.keep_lit) {
    return false;
  }
  s_state.keep_lit = keep_lit;
  return true;
}

NavChange nav_state_apply(DictionaryIterator *iter) {
  NavChange change = NAV_CHANGE_NONE;
  bool instruction_changed = false;
  bool text_changed = false;
  bool was_active = s_state.active;

  text_changed |= apply_active(iter);
  instruction_changed |= apply_maneuver(iter);
  instruction_changed |= apply_arrow(iter);
  instruction_changed |= copy_text(s_state.street, dict_find(iter, KEY_STREET));
  instruction_changed |= copy_text(s_state.instruction, dict_find(iter, KEY_INSTRUCTION));
  text_changed |= copy_text(s_state.distance, dict_find(iter, KEY_DISTANCE));
  text_changed |= copy_text(s_state.eta, dict_find(iter, KEY_ETA));
  text_changed |= copy_text(s_state.dist_remain, dict_find(iter, KEY_DIST_REMAIN));
  text_changed |= copy_text(s_state.time_remain, dict_find(iter, KEY_TIME_REMAIN));

  if (text_changed || instruction_changed) {
    change |= NAV_CHANGE_TEXT;
  }
  if (instruction_changed && s_state.active) {
    change |= NAV_CHANGE_INSTRUCTION;
  }
  if (apply_keep_lit(iter) || (was_active && !s_state.active)) {
    change |= NAV_CHANGE_BACKLIGHT;
  }
  return change;
}

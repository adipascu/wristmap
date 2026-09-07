#pragma once
#include <pebble.h>
#include "protocol.h"

typedef enum {
  MANEUVER_UNKNOWN = 0,
  MANEUVER_STRAIGHT = 1,
  MANEUVER_TURN_LEFT = 2,
  MANEUVER_TURN_RIGHT = 3,
  MANEUVER_SLIGHT_LEFT = 4,
  MANEUVER_SLIGHT_RIGHT = 5,
  MANEUVER_SHARP_LEFT = 6,
  MANEUVER_SHARP_RIGHT = 7,
  MANEUVER_UTURN = 8,
  MANEUVER_MERGE = 9,
  MANEUVER_ROUNDABOUT = 10,
  MANEUVER_RAMP = 11,
  MANEUVER_DESTINATION = 12,
} Maneuver;

#define NAV_TEXT_LEN 48

typedef struct {
  bool active;
  Maneuver maneuver;
  bool has_arrow;
  uint8_t arrow[ARROW_BYTES];
  char distance[NAV_TEXT_LEN];
  char street[NAV_TEXT_LEN];
  char instruction[NAV_TEXT_LEN];
  char eta[NAV_TEXT_LEN];
  char dist_remain[NAV_TEXT_LEN];
  char time_remain[NAV_TEXT_LEN];
  bool keep_lit;
} NavState;

typedef enum {
  NAV_CHANGE_NONE = 0,
  NAV_CHANGE_TEXT = 1 << 0,
  NAV_CHANGE_INSTRUCTION = 1 << 1,
  NAV_CHANGE_BACKLIGHT = 1 << 2,
} NavChange;

const NavState *nav_state_get(void);
NavChange nav_state_apply(DictionaryIterator *iter);

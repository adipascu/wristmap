#pragma once
#include <pebble.h>
#include "nav_state.h"

void arrows_draw_packed(GContext *ctx, GRect box, const uint8_t *data, int scale, GColor color);
void arrows_draw_fallback(GContext *ctx, GRect box, Maneuver maneuver, GColor color);

#pragma once
#include <pebble.h>

GRect ui_map_area(GRect bounds);
GPoint ui_map_anchor(GRect bounds);
void ui_draw(GContext *ctx, GRect bounds, int32_t map_scale_256);

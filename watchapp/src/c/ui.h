#pragma once
#include <pebble.h>

typedef enum {
  VIEW_MAP = 0,
  VIEW_ARROW = 1,
} ViewMode;

GRect ui_map_area(GRect bounds);
void ui_draw(GContext *ctx, GRect bounds, ViewMode mode);

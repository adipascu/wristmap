#pragma once
#include <pebble.h>

void map_frame_init(void);
void map_frame_set_max_size(GSize size);
void map_frame_clear(void);
void map_frame_deinit(void);
bool map_frame_apply(DictionaryIterator *iter);
bool map_frame_has_bitmap(void);
uint16_t map_frame_zoom(void);
void map_frame_draw(GContext *ctx, GRect area, int32_t scale_256, GPoint anchor);

#pragma once
#include <pebble.h>

void map_frame_init(void);
void map_frame_set_max_size(GSize size);
void map_frame_clear(void);
void map_frame_deinit(void);
bool map_frame_apply(DictionaryIterator *iter);
GBitmap *map_frame_bitmap(void);
bool map_frame_receiving(void);

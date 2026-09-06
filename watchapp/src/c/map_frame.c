#include "map_frame.h"
#include "protocol.h"

static GColor s_palette[4];
static uint8_t *s_staging;
static uint32_t s_staging_capacity;
static uint32_t s_total;
static uint32_t s_received;
static uint16_t s_width;
static uint16_t s_height;
static uint8_t s_frame;
static bool s_frame_open;
static GBitmap *s_bitmap;
static GSize s_max_size;

void map_frame_init(void) {
  s_palette[0] = GColorWhite;
  s_palette[1] = GColorBlack;
  s_palette[2] = GColorLightGray;
  s_palette[3] = GColorBlue;
}

void map_frame_set_max_size(GSize size) {
  s_max_size = size;
}

void map_frame_clear(void) {
  s_frame_open = false;
  if (s_bitmap) {
    gbitmap_destroy(s_bitmap);
    s_bitmap = NULL;
  }
}

void map_frame_deinit(void) {
  map_frame_clear();
  free(s_staging);
  s_staging = NULL;
  s_staging_capacity = 0;
}

GBitmap *map_frame_bitmap(void) {
  return s_bitmap;
}

bool map_frame_receiving(void) {
  return s_frame_open;
}

static bool ensure_staging(uint32_t size) {
  if (s_staging && s_staging_capacity >= size) {
    return true;
  }
  free(s_staging);
  s_staging = malloc(size);
  s_staging_capacity = s_staging ? size : 0;
  if (!s_staging) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "no memory for %lu byte frame", (unsigned long)size);
  }
  return s_staging != NULL;
}

static bool ensure_bitmap(uint16_t width, uint16_t height) {
  if (s_bitmap) {
    GRect bounds = gbitmap_get_bounds(s_bitmap);
    if (bounds.size.w == width && bounds.size.h == height) {
      return true;
    }
    gbitmap_destroy(s_bitmap);
    s_bitmap = NULL;
  }
  s_bitmap = gbitmap_create_blank_with_palette(GSize(width, height), GBitmapFormat2BitPalette,
                                               s_palette, false);
  if (!s_bitmap) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "no memory for %dx%d bitmap", width, height);
  }
  return s_bitmap != NULL;
}

static bool begin_frame(uint8_t frame, uint16_t width, uint16_t height, uint32_t total) {
  uint32_t expected = ((uint32_t)width * 2 + 7) / 8 * height;
  bool fits = width > 0 && height > 0 && width <= s_max_size.w && height <= s_max_size.h;
  if (total != expected || !fits) {
    APP_LOG(APP_LOG_LEVEL_WARNING, "bad frame header %dx%d total %lu", width, height,
            (unsigned long)total);
    s_frame_open = false;
    return false;
  }
  if (!ensure_staging(total)) {
    s_frame_open = false;
    return false;
  }
  s_frame = frame;
  s_width = width;
  s_height = height;
  s_total = total;
  s_received = 0;
  s_frame_open = true;
  return true;
}

static void commit_frame(void) {
  s_frame_open = false;
  if (!ensure_bitmap(s_width, s_height)) {
    return;
  }
  uint16_t src_stride = (s_width * 2 + 7) / 8;
  uint16_t dst_stride = gbitmap_get_bytes_per_row(s_bitmap);
  uint8_t *dst = gbitmap_get_data(s_bitmap);
  for (uint16_t y = 0; y < s_height; y++) {
    memcpy(dst + (uint32_t)y * dst_stride, s_staging + (uint32_t)y * src_stride, src_stride);
  }
}

bool map_frame_apply(DictionaryIterator *iter) {
  Tuple *frame_tuple = dict_find(iter, KEY_MAP_FRAME);
  if (!frame_tuple) {
    return false;
  }
  uint8_t frame = frame_tuple->value->uint8;

  Tuple *width_tuple = dict_find(iter, KEY_MAP_WIDTH);
  Tuple *height_tuple = dict_find(iter, KEY_MAP_HEIGHT);
  Tuple *total_tuple = dict_find(iter, KEY_MAP_TOTAL);
  if (width_tuple && height_tuple && total_tuple) {
    if (!begin_frame(frame, width_tuple->value->uint16, height_tuple->value->uint16,
                     total_tuple->value->uint32)) {
      return false;
    }
  } else if (!s_frame_open || frame != s_frame) {
    APP_LOG(APP_LOG_LEVEL_WARNING, "chunk for frame %d dropped, open frame %d", frame,
            s_frame_open ? s_frame : -1);
    return false;
  }

  Tuple *data_tuple = dict_find(iter, KEY_MAP_DATA);
  Tuple *offset_tuple = dict_find(iter, KEY_MAP_OFFSET);
  if (!data_tuple || !offset_tuple) {
    return false;
  }
  uint32_t offset = offset_tuple->value->uint32;
  uint32_t length = data_tuple->length;
  if (offset < s_received) {
    return false;
  }
  if (offset != s_received || length > s_total - offset) {
    APP_LOG(APP_LOG_LEVEL_WARNING, "chunk out of sequence: %lu+%lu, have %lu of %lu",
            (unsigned long)offset, (unsigned long)length, (unsigned long)s_received,
            (unsigned long)s_total);
    s_frame_open = false;
    return false;
  }
  memcpy(s_staging + offset, data_tuple->value->data, length);
  s_received += length;
  if (s_received < s_total) {
    return false;
  }
  commit_frame();
  return true;
}

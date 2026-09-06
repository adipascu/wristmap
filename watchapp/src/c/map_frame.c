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
static GBitmap *s_scaled;
static GSize s_max_size;
static uint16_t s_zoom;
static uint16_t s_pending_zoom;

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
  s_zoom = 0;
  if (s_bitmap) {
    gbitmap_destroy(s_bitmap);
    s_bitmap = NULL;
  }
  if (s_scaled) {
    gbitmap_destroy(s_scaled);
    s_scaled = NULL;
  }
}

void map_frame_deinit(void) {
  map_frame_clear();
  free(s_staging);
  s_staging = NULL;
  s_staging_capacity = 0;
}

bool map_frame_has_bitmap(void) {
  return s_bitmap != NULL;
}

uint16_t map_frame_zoom(void) {
  return s_zoom;
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
    if (s_scaled) {
      gbitmap_destroy(s_scaled);
      s_scaled = NULL;
    }
  }
  s_bitmap = gbitmap_create_blank_with_palette(GSize(width, height), GBitmapFormat2BitPalette,
                                               s_palette, false);
  if (!s_bitmap) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "no memory for %dx%d bitmap", width, height);
  }
  return s_bitmap != NULL;
}

static bool begin_frame(uint8_t frame, uint16_t width, uint16_t height, uint32_t total,
                        uint16_t zoom) {
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
  s_pending_zoom = (zoom >= ZOOM_MIN && zoom <= ZOOM_MAX) ? zoom : 0;
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
  s_zoom = s_pending_zoom;
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
  Tuple *zoom_tuple = dict_find(iter, KEY_MAP_ZOOM);
  if (width_tuple && height_tuple && total_tuple) {
    if (!begin_frame(frame, width_tuple->value->uint16, height_tuple->value->uint16,
                     total_tuple->value->uint32, zoom_tuple ? zoom_tuple->value->uint16 : 0)) {
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

static uint8_t pixel_index(const uint8_t *row, int16_t x) {
  return (row[x >> 2] >> (6 - 2 * (x & 3))) & 3;
}

static void put_pixel_index(uint8_t *row, int16_t x, uint8_t index) {
  int shift = 6 - 2 * (x & 3);
  row[x >> 2] = (row[x >> 2] & ~(3 << shift)) | (index << shift);
}

static bool ensure_scaled(void) {
  if (s_scaled) {
    return true;
  }
  s_scaled = gbitmap_create_blank_with_palette(GSize(s_width, s_height), GBitmapFormat2BitPalette,
                                               s_palette, false);
  return s_scaled != NULL;
}

static void render_scaled(int32_t scale_256, GPoint anchor) {
  uint16_t src_stride = gbitmap_get_bytes_per_row(s_bitmap);
  uint16_t dst_stride = gbitmap_get_bytes_per_row(s_scaled);
  const uint8_t *src = gbitmap_get_data(s_bitmap);
  uint8_t *dst = gbitmap_get_data(s_scaled);
  int32_t inverse_256 = (256 * 256) / scale_256;
  for (int16_t y = 0; y < s_height; y++) {
    int32_t source_y = anchor.y + (((int32_t)(y - anchor.y)) * inverse_256 >> 8);
    uint8_t *dst_row = dst + (uint32_t)y * dst_stride;
    memset(dst_row, 0, dst_stride);
    if (source_y < 0 || source_y >= s_height) {
      continue;
    }
    const uint8_t *src_row = src + (uint32_t)source_y * src_stride;
    for (int16_t x = 0; x < s_width; x++) {
      int32_t source_x = anchor.x + (((int32_t)(x - anchor.x)) * inverse_256 >> 8);
      if (source_x < 0 || source_x >= s_width) {
        continue;
      }
      uint8_t index = pixel_index(src_row, (int16_t)source_x);
      if (index) {
        put_pixel_index(dst_row, x, index);
      }
    }
  }
}

void map_frame_draw(GContext *ctx, GRect area, int32_t scale_256, GPoint anchor) {
  if (!s_bitmap) {
    return;
  }
  GRect dest = GRect(area.origin.x + (area.size.w - s_width) / 2,
                     area.origin.y + (area.size.h - s_height) / 2, s_width, s_height);
  GBitmap *bitmap = s_bitmap;
  if (scale_256 != 256 && ensure_scaled()) {
    render_scaled(scale_256, GPoint(anchor.x - dest.origin.x, anchor.y - dest.origin.y));
    bitmap = s_scaled;
  }
  graphics_context_set_compositing_mode(ctx, GCompOpAssign);
  graphics_draw_bitmap_in_rect(ctx, bitmap, dest);
}

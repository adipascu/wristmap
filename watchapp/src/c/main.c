#include <pebble.h>
#include "haptics.h"
#include "map_frame.h"
#include "nav_state.h"
#include "protocol.h"
#include "ui.h"

#define PIXELS_PER_ZOOM_LEVEL 60
#define LIGHT_HOLD_MS 5000
#define ZOOM_SEND_INTERVAL_MS 120
#define ZOOM_SEND_MAX_RETRIES 5
#define DEFAULT_ZOOM (17 * ZOOM_SCALE)

static Window *s_window;
static Layer *s_layer;
static AppTimer *s_light_timer;
static AppTimer *s_zoom_timer;
static bool s_touching;
static int16_t s_touch_start_y;
static int32_t s_touch_start_zoom;
static int32_t s_zoom_target;
static bool s_zoom_unsent;
static uint8_t s_frames_since_liftoff;
static uint8_t s_zoom_send_failures;

static int32_t pow2_256(int32_t exponent_100) {
  int32_t whole = exponent_100 >= 0 ? exponent_100 / ZOOM_SCALE : -((-exponent_100 + ZOOM_SCALE - 1) / ZOOM_SCALE);
  int32_t fraction_256 = (exponent_100 - whole * ZOOM_SCALE) * 256 / ZOOM_SCALE;
  int32_t scaled = 256 + ((fraction_256 * (168 + ((88 * fraction_256) >> 8))) >> 8);
  return whole >= 0 ? scaled << whole : scaled >> -whole;
}

static int32_t current_zoom(void) {
  if (s_zoom_target) {
    return s_zoom_target;
  }
  uint16_t frame_zoom = map_frame_zoom();
  return frame_zoom ? frame_zoom : DEFAULT_ZOOM;
}

static void layer_update(Layer *layer, GContext *ctx) {
  int32_t scale_256 = 256;
  uint16_t frame_zoom = map_frame_zoom();
  if (frame_zoom && s_zoom_target && s_zoom_target != frame_zoom) {
    scale_256 = pow2_256(s_zoom_target - frame_zoom);
  }
  ui_draw(ctx, layer_get_bounds(layer), scale_256);
}

static void send_hello(void) {
  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) != APP_MSG_OK) {
    return;
  }
  GRect map_area = ui_map_area(layer_get_bounds(s_layer));
  dict_write_uint8(out, KEY_HELLO, 1);
  dict_write_uint32(out, KEY_INBOX_MAX, app_message_inbox_size_maximum());
  dict_write_uint16(out, KEY_MAP_VIEW_WIDTH, map_area.size.w);
  dict_write_uint16(out, KEY_MAP_VIEW_HEIGHT, map_area.size.h);
  app_message_outbox_send();
}

static void zoom_timer_fired(void *context);

static void send_zoom_level(void) {
  if (!s_zoom_target) {
    s_zoom_unsent = false;
    return;
  }
  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) != APP_MSG_OK) {
    s_zoom_unsent = true;
    return;
  }
  dict_write_uint16(out, KEY_ZOOM_LEVEL, s_zoom_target);
  app_message_outbox_send();
  s_zoom_unsent = false;
}

static void zoom_timer_fired(void *context) {
  s_zoom_timer = NULL;
  if (s_zoom_unsent) {
    send_zoom_level();
    s_zoom_timer = app_timer_register(ZOOM_SEND_INTERVAL_MS, zoom_timer_fired, NULL);
  }
}

static void zoom_changed(void) {
  layer_mark_dirty(s_layer);
  s_zoom_unsent = true;
  if (!s_zoom_timer) {
    send_zoom_level();
    s_zoom_timer = app_timer_register(ZOOM_SEND_INTERVAL_MS, zoom_timer_fired, NULL);
  }
}

static void forget_zoom(void) {
  s_zoom_target = 0;
  s_zoom_unsent = false;
  s_touching = false;
  s_frames_since_liftoff = 0;
  s_zoom_send_failures = 0;
  if (s_zoom_timer) {
    app_timer_cancel(s_zoom_timer);
    s_zoom_timer = NULL;
  }
}

static void light_timer_fired(void *context) {
  s_light_timer = NULL;
  light_enable(false);
}

static void hold_light(void) {
  light_enable(true);
  if (s_light_timer) {
    app_timer_reschedule(s_light_timer, LIGHT_HOLD_MS);
  } else {
    s_light_timer = app_timer_register(LIGHT_HOLD_MS, light_timer_fired, NULL);
  }
}

static void touch_handler(const TouchEvent *event, void *context) {
  switch (event->type) {
    case TouchEvent_Touchdown:
      hold_light();
      s_touching = true;
      s_frames_since_liftoff = 0;
      s_zoom_send_failures = 0;
      s_touch_start_y = event->y;
      s_touch_start_zoom = current_zoom();
      break;
    case TouchEvent_PositionUpdate: {
      if (!s_touching) {
        break;
      }
      hold_light();
      int32_t delta = (int32_t)(s_touch_start_y - event->y) * ZOOM_SCALE / PIXELS_PER_ZOOM_LEVEL;
      int32_t target = s_touch_start_zoom + delta;
      if (target < ZOOM_MIN) {
        target = ZOOM_MIN;
      } else if (target > ZOOM_MAX) {
        target = ZOOM_MAX;
      }
      if (target != s_zoom_target) {
        s_zoom_target = target;
        zoom_changed();
      }
      break;
    }
    case TouchEvent_Liftoff:
      s_touching = false;
      if (s_zoom_unsent && !s_zoom_timer) {
        send_zoom_level();
      }
      break;
  }
}

static void inbox_received(DictionaryIterator *iter, void *context) {
  bool was_active = nav_state_get()->active;
  NavChange change = nav_state_apply(iter);
  if (was_active && !nav_state_get()->active) {
    map_frame_clear();
    forget_zoom();
  }
  bool frame_done = map_frame_apply(iter);
  if (frame_done && s_zoom_target && !s_touching && !s_zoom_unsent) {
    if (map_frame_zoom() == s_zoom_target || ++s_frames_since_liftoff >= 2) {
      s_zoom_target = 0;
      s_frames_since_liftoff = 0;
    }
  }
  bool cued = haptics_apply(iter);
  if (!cued && (change & NAV_CHANGE_INSTRUCTION)) {
    vibes_short_pulse();
  }
  if (change != NAV_CHANGE_NONE || frame_done) {
    layer_mark_dirty(s_layer);
  }
}

static void inbox_dropped(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_WARNING, "inbox dropped: %d", reason);
}

static void outbox_failed(DictionaryIterator *iter, AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_WARNING, "outbox failed: %d", reason);
  if (dict_find(iter, KEY_ZOOM_LEVEL) && s_zoom_target && s_zoom_send_failures < ZOOM_SEND_MAX_RETRIES) {
    s_zoom_send_failures++;
    s_zoom_unsent = true;
    if (!s_zoom_timer) {
      s_zoom_timer = app_timer_register(ZOOM_SEND_INTERVAL_MS, zoom_timer_fired, NULL);
    }
  }
}

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(root);
  s_layer = layer_create(bounds);
  layer_set_update_proc(s_layer, layer_update);
  layer_add_child(root, s_layer);
  map_frame_set_max_size(ui_map_area(bounds).size);
}

static void window_unload(Window *window) {
  layer_destroy(s_layer);
}

static void init(void) {
  map_frame_init();
  s_window = window_create();
  window_set_background_color(s_window, GColorWhite);
  window_set_touch_bridge_disabled(s_window, true);
  window_set_window_handlers(s_window, (WindowHandlers){
    .load = window_load,
    .unload = window_unload,
  });

  app_message_register_inbox_received(inbox_received);
  app_message_register_inbox_dropped(inbox_dropped);
  app_message_register_outbox_failed(outbox_failed);
  app_message_open(app_message_inbox_size_maximum(), 64);

  window_stack_push(s_window, true);
  touch_service_subscribe(touch_handler, NULL);
  send_hello();
}

static void deinit(void) {
  touch_service_unsubscribe();
  light_enable(false);
  window_destroy(s_window);
  map_frame_deinit();
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

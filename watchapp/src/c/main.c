#include <pebble.h>
#include "map_frame.h"
#include "nav_state.h"
#include "protocol.h"
#include "ui.h"

static Window *s_window;
static Layer *s_layer;
static ViewMode s_view_mode = VIEW_MAP;

static void layer_update(Layer *layer, GContext *ctx) {
  ui_draw(ctx, layer_get_bounds(layer), s_view_mode);
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

static void send_zoom(uint8_t direction) {
  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) != APP_MSG_OK) {
    return;
  }
  dict_write_uint8(out, KEY_ZOOM, direction);
  app_message_outbox_send();
}

static void inbox_received(DictionaryIterator *iter, void *context) {
  bool was_active = nav_state_get()->active;
  NavChange change = nav_state_apply(iter);
  if (was_active && !nav_state_get()->active) {
    map_frame_clear();
  }
  bool frame_done = map_frame_apply(iter);
  if (change & NAV_CHANGE_INSTRUCTION) {
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
}

static void up_click(ClickRecognizerRef recognizer, void *context) {
  send_zoom(ZOOM_IN);
}

static void down_click(ClickRecognizerRef recognizer, void *context) {
  send_zoom(ZOOM_OUT);
}

static void select_click(ClickRecognizerRef recognizer, void *context) {
  s_view_mode = s_view_mode == VIEW_MAP ? VIEW_ARROW : VIEW_MAP;
  layer_mark_dirty(s_layer);
}

static void click_config(void *context) {
  window_single_click_subscribe(BUTTON_ID_UP, up_click);
  window_single_click_subscribe(BUTTON_ID_DOWN, down_click);
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click);
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
  window_set_click_config_provider(s_window, click_config);
  window_set_window_handlers(s_window, (WindowHandlers){
    .load = window_load,
    .unload = window_unload,
  });

  app_message_register_inbox_received(inbox_received);
  app_message_register_inbox_dropped(inbox_dropped);
  app_message_register_outbox_failed(outbox_failed);
  app_message_open(app_message_inbox_size_maximum(), 64);

  window_stack_push(s_window, true);
  send_hello();
}

static void deinit(void) {
  window_destroy(s_window);
  map_frame_deinit();
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

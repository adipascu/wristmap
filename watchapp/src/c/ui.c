#include "ui.h"
#include "arrows.h"
#include "map_frame.h"
#include "nav_state.h"

#define TOP_BAR_HEIGHT 58
#define BOTTOM_BAR_HEIGHT 18
#define ARROW_BOX 54
#define MARKER_FRACTION_PERCENT 70

GRect ui_map_area(GRect bounds) {
  return GRect(bounds.origin.x, bounds.origin.y + TOP_BAR_HEIGHT, bounds.size.w,
               bounds.size.h - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT);
}

GPoint ui_map_anchor(GRect bounds) {
  GRect area = ui_map_area(bounds);
  return GPoint(area.origin.x + area.size.w / 2, area.origin.y + area.size.h * MARKER_FRACTION_PERCENT / 100);
}

static void draw_text(GContext *ctx, const char *text, const char *font_key, GRect frame,
                      GTextAlignment alignment) {
  graphics_context_set_text_color(ctx, GColorBlack);
  graphics_draw_text(ctx, text, fonts_get_system_font(font_key), frame,
                     GTextOverflowModeTrailingEllipsis, alignment, NULL);
}

static void draw_maneuver(GContext *ctx, GRect box, const NavState *state, int scale) {
  if (state->has_arrow) {
    arrows_draw_packed(ctx, box, state->arrow, scale, GColorBlack);
  } else {
    arrows_draw_fallback(ctx, box, state->maneuver, GColorBlack);
  }
}

static void build_trip_line(const NavState *state, char *out, size_t capacity) {
  out[0] = '\0';
  const char *parts[3] = {state->time_remain, state->dist_remain, state->eta};
  for (int i = 0; i < 3; i++) {
    if (!parts[i][0]) {
      continue;
    }
    if (out[0]) {
      strncat(out, "   ", capacity - strlen(out) - 1);
    }
    strncat(out, parts[i], capacity - strlen(out) - 1);
  }
}

static void draw_idle(GContext *ctx, GRect bounds) {
  GRect title = GRect(bounds.origin.x + 8, bounds.origin.y + bounds.size.h / 2 - 44, bounds.size.w - 16, 60);
  draw_text(ctx, "Wristmap", FONT_KEY_GOTHIC_28_BOLD, title, GTextAlignmentCenter);
  GRect hint = GRect(bounds.origin.x + 8, bounds.origin.y + bounds.size.h / 2 - 6, bounds.size.w - 16, 80);
  draw_text(ctx, "Start walking or cycling directions in Google Maps", FONT_KEY_GOTHIC_18, hint,
            GTextAlignmentCenter);
}

static void draw_top_bar(GContext *ctx, GRect bounds, const NavState *state) {
  GRect arrow_box = GRect(bounds.origin.x + 2, bounds.origin.y + 2, ARROW_BOX, ARROW_BOX);
  draw_maneuver(ctx, arrow_box, state, 1);

  int text_x = bounds.origin.x + ARROW_BOX + 4;
  int text_w = bounds.size.w - ARROW_BOX - 6;
  draw_text(ctx, state->distance, FONT_KEY_GOTHIC_28_BOLD, GRect(text_x, bounds.origin.y - 2, text_w, 32),
            GTextAlignmentLeft);
  const char *street = state->street[0] ? state->street : state->instruction;
  draw_text(ctx, street, FONT_KEY_GOTHIC_18, GRect(text_x, bounds.origin.y + 30, text_w, 24),
            GTextAlignmentLeft);

  graphics_context_set_stroke_color(ctx, GColorBlack);
  graphics_context_set_stroke_width(ctx, 1);
  int line_y = bounds.origin.y + TOP_BAR_HEIGHT - 1;
  graphics_draw_line(ctx, GPoint(bounds.origin.x, line_y), GPoint(bounds.origin.x + bounds.size.w, line_y));
}

static void draw_bottom_bar(GContext *ctx, GRect bounds, const NavState *state) {
  char trip[NAV_TEXT_LEN * 3];
  build_trip_line(state, trip, sizeof(trip));
  GRect frame = GRect(bounds.origin.x, bounds.origin.y + bounds.size.h - BOTTOM_BAR_HEIGHT - 2,
                      bounds.size.w, BOTTOM_BAR_HEIGHT + 2);
  draw_text(ctx, trip, FONT_KEY_GOTHIC_14_BOLD, frame, GTextAlignmentCenter);
}

static void draw_map(GContext *ctx, GRect area, int32_t scale_256, GPoint anchor) {
  if (!map_frame_has_bitmap()) {
    GRect frame = GRect(area.origin.x + 8, area.origin.y + area.size.h / 2 - 12, area.size.w - 16, 40);
    draw_text(ctx, "Waiting for map...", FONT_KEY_GOTHIC_18, frame, GTextAlignmentCenter);
    return;
  }
  map_frame_draw(ctx, area, scale_256, anchor);
}

void ui_draw(GContext *ctx, GRect bounds, int32_t map_scale_256) {
  const NavState *state = nav_state_get();
  graphics_context_set_fill_color(ctx, GColorWhite);
  graphics_fill_rect(ctx, bounds, 0, GCornerNone);
  if (!state->active) {
    draw_idle(ctx, bounds);
    return;
  }
  draw_top_bar(ctx, bounds, state);
  draw_map(ctx, ui_map_area(bounds), map_scale_256, ui_map_anchor(bounds));
  draw_bottom_bar(ctx, bounds, state);
}

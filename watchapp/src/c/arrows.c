#include "arrows.h"

void arrows_draw_packed(GContext *ctx, GRect box, const uint8_t *data, int scale, GColor color) {
  if (scale < 1) {
    scale = 1;
  }
  int drawn = ARROW_SIZE * scale;
  int origin_x = box.origin.x + (box.size.w - drawn) / 2;
  int origin_y = box.origin.y + (box.size.h - drawn) / 2;
  graphics_context_set_fill_color(ctx, color);
  for (int y = 0; y < ARROW_SIZE; y++) {
    for (int x = 0; x < ARROW_SIZE; x++) {
      uint8_t byte = data[y * ARROW_ROW_BYTES + (x >> 3)];
      if ((byte >> (7 - (x & 7))) & 1) {
        graphics_fill_rect(ctx, GRect(origin_x + x * scale, origin_y + y * scale, scale, scale), 0,
                           GCornerNone);
      }
    }
  }
}

static GPoint polar(GPoint center, int radius, int degrees) {
  int32_t angle = DEG_TO_TRIGANGLE(degrees);
  return GPoint(center.x + (sin_lookup(angle) * radius) / TRIG_MAX_RATIO,
                center.y - (cos_lookup(angle) * radius) / TRIG_MAX_RATIO);
}

static void draw_head(GContext *ctx, GPoint tip, int degrees, int size) {
  GPoint left = polar(tip, size, degrees + 150);
  GPoint right = polar(tip, size, degrees - 150);
  GPathInfo info = {.num_points = 3, .points = (GPoint[]){tip, left, right}};
  GPath *path = gpath_create(&info);
  gpath_draw_filled(ctx, path);
  gpath_destroy(path);
}

static void draw_turn(GContext *ctx, GRect box, int degrees) {
  int side = box.size.w < box.size.h ? box.size.w : box.size.h;
  GPoint center = GPoint(box.origin.x + box.size.w / 2, box.origin.y + box.size.h / 2);
  int stroke = side / 8 < 3 ? 3 : side / 8;
  graphics_context_set_stroke_width(ctx, stroke);
  GPoint start = GPoint(center.x, center.y + side * 4 / 10);
  GPoint corner = GPoint(center.x, center.y + side / 10);
  GPoint tip = polar(corner, side * 4 / 10, degrees);
  graphics_draw_line(ctx, start, corner);
  graphics_draw_line(ctx, corner, tip);
  graphics_fill_circle(ctx, corner, stroke / 2);
  draw_head(ctx, tip, degrees, side / 4);
}

static void draw_uturn(GContext *ctx, GRect box) {
  int side = box.size.w < box.size.h ? box.size.w : box.size.h;
  GPoint center = GPoint(box.origin.x + box.size.w / 2, box.origin.y + box.size.h / 2);
  int stroke = side / 8 < 3 ? 3 : side / 8;
  int radius = side / 5;
  graphics_context_set_stroke_width(ctx, stroke);
  GPoint right_bottom = GPoint(center.x + radius, center.y + side * 4 / 10);
  GPoint right_top = GPoint(center.x + radius, center.y - side / 10);
  GPoint left_top = GPoint(center.x - radius, center.y - side / 10);
  GPoint left_bottom = GPoint(center.x - radius, center.y + side / 10);
  graphics_draw_line(ctx, right_bottom, right_top);
  graphics_draw_arc(ctx,
                    GRect(center.x - radius, center.y - side / 10 - radius, radius * 2, radius * 2),
                    GOvalScaleModeFitCircle, DEG_TO_TRIGANGLE(270), DEG_TO_TRIGANGLE(450));
  graphics_draw_line(ctx, left_top, left_bottom);
  draw_head(ctx, GPoint(left_bottom.x, left_bottom.y + side / 8), 180, side / 4);
}

static void draw_roundabout(GContext *ctx, GRect box) {
  int side = box.size.w < box.size.h ? box.size.w : box.size.h;
  GPoint center = GPoint(box.origin.x + box.size.w / 2, box.origin.y + box.size.h / 2 + side / 10);
  int stroke = side / 8 < 3 ? 3 : side / 8;
  graphics_context_set_stroke_width(ctx, stroke);
  graphics_draw_circle(ctx, center, side / 5);
  GPoint top = GPoint(center.x, center.y - side / 5);
  GPoint tip = GPoint(center.x, center.y - side / 2);
  graphics_draw_line(ctx, top, tip);
  draw_head(ctx, tip, 0, side / 4);
}

static void draw_destination(GContext *ctx, GRect box) {
  int side = box.size.w < box.size.h ? box.size.w : box.size.h;
  GPoint center = GPoint(box.origin.x + box.size.w / 2, box.origin.y + box.size.h / 2);
  int stroke = side / 10 < 2 ? 2 : side / 10;
  graphics_context_set_stroke_width(ctx, stroke);
  GPoint pole_top = GPoint(center.x - side / 4, center.y - side * 4 / 10);
  GPoint pole_bottom = GPoint(center.x - side / 4, center.y + side * 4 / 10);
  graphics_draw_line(ctx, pole_top, pole_bottom);
  graphics_fill_rect(ctx, GRect(pole_top.x, pole_top.y, side / 2, side / 3), 0, GCornerNone);
}

static void draw_merge(GContext *ctx, GRect box) {
  int side = box.size.w < box.size.h ? box.size.w : box.size.h;
  GPoint center = GPoint(box.origin.x + box.size.w / 2, box.origin.y + box.size.h / 2);
  int stroke = side / 8 < 3 ? 3 : side / 8;
  graphics_context_set_stroke_width(ctx, stroke);
  GPoint join = GPoint(center.x, center.y);
  GPoint tip = GPoint(center.x, center.y - side * 4 / 10);
  graphics_draw_line(ctx, GPoint(center.x - side / 4, center.y + side * 4 / 10), join);
  graphics_draw_line(ctx, GPoint(center.x + side / 4, center.y + side * 4 / 10), join);
  graphics_draw_line(ctx, join, tip);
  draw_head(ctx, tip, 0, side / 4);
}

void arrows_draw_fallback(GContext *ctx, GRect box, Maneuver maneuver, GColor color) {
  graphics_context_set_stroke_color(ctx, color);
  graphics_context_set_fill_color(ctx, color);
  switch (maneuver) {
    case MANEUVER_STRAIGHT:
      draw_turn(ctx, box, 0);
      break;
    case MANEUVER_TURN_LEFT:
      draw_turn(ctx, box, -90);
      break;
    case MANEUVER_TURN_RIGHT:
      draw_turn(ctx, box, 90);
      break;
    case MANEUVER_SLIGHT_LEFT:
      draw_turn(ctx, box, -40);
      break;
    case MANEUVER_SLIGHT_RIGHT:
    case MANEUVER_RAMP:
      draw_turn(ctx, box, 40);
      break;
    case MANEUVER_SHARP_LEFT:
      draw_turn(ctx, box, -135);
      break;
    case MANEUVER_SHARP_RIGHT:
      draw_turn(ctx, box, 135);
      break;
    case MANEUVER_UTURN:
      draw_uturn(ctx, box);
      break;
    case MANEUVER_MERGE:
      draw_merge(ctx, box);
      break;
    case MANEUVER_ROUNDABOUT:
      draw_roundabout(ctx, box);
      break;
    case MANEUVER_DESTINATION:
      draw_destination(ctx, box);
      break;
    case MANEUVER_UNKNOWN:
    default:
      graphics_context_set_text_color(ctx, color);
      graphics_draw_text(ctx, "?", fonts_get_system_font(FONT_KEY_GOTHIC_28_BOLD), box,
                         GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
      break;
  }
}

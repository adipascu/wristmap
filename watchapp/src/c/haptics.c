#include "haptics.h"
#include "protocol.h"

#define MAX_SEGMENTS 32

static uint32_t s_durations[MAX_SEGMENTS];

bool haptics_apply(DictionaryIterator *iter) {
  Tuple *tuple = dict_find(iter, KEY_HAPTIC_PATTERN);
  if (!tuple || tuple->length < 2) {
    return false;
  }
  uint32_t count = tuple->length / 2;
  if (count > MAX_SEGMENTS) {
    count = MAX_SEGMENTS;
  }
  const uint8_t *bytes = tuple->value->data;
  for (uint32_t i = 0; i < count; i++) {
    s_durations[i] = bytes[2 * i] | (bytes[2 * i + 1] << 8);
  }
  vibes_cancel();
  vibes_enqueue_custom_pattern((VibePattern){.durations = s_durations, .num_segments = count});
  return true;
}

#!/usr/bin/env python3
"""Drive the Maps Navigation watchapp in the Pebble emulator without a phone.

Run with pebble-tool's Python so libpebble2 and pebble_tool are importable:
  ~/.local/share/uv/tools/pebble-tool/bin/python scripts/emu-send.py --demo
  ~/.local/share/uv/tools/pebble-tool/bin/python scripts/emu-send.py --frame frame.wmf
"""

import argparse
import struct
import threading
import time
import uuid

from libpebble2.communication import PebbleConnection
from libpebble2.protocol.apps import AppRunState, AppRunStateStart, AppRunStateStop
from libpebble2.services.appmessage import (
    AppMessageService,
    ByteArray,
    CString,
    Uint8,
    Uint16,
    Uint32,
)
from pebble_tool.sdk.emulator import ManagedEmulatorTransport

APP_UUID = uuid.UUID("58b9be94-3f7b-4338-94e5-90890b2ab7a0")

KEY_NAV_ACTIVE = 1
KEY_MANEUVER = 2
KEY_ARROW_BITMAP = 3
KEY_DISTANCE = 4
KEY_STREET = 5
KEY_INSTRUCTION = 6
KEY_ETA = 7
KEY_DIST_REMAIN = 8
KEY_TIME_REMAIN = 9
KEY_MAP_WIDTH = 20
KEY_MAP_HEIGHT = 21
KEY_MAP_FRAME = 22
KEY_MAP_TOTAL = 23
KEY_MAP_OFFSET = 24
KEY_MAP_DATA = 25
KEY_MAP_ZOOM = 26

hello = {}


class Link:
    def __init__(self, platform):
        transport = ManagedEmulatorTransport(platform)
        self.pebble = PebbleConnection(transport)
        self.pebble.connect()
        self.pebble.run_async()
        self.service = AppMessageService(self.pebble)
        self.acks = {}
        self.service.register_handler("ack", self._on_ack)
        self.service.register_handler("nack", self._on_nack)
        self.service.register_handler("appmessage", self._on_message)

    def _on_ack(self, tid, app_uuid):
        event = self.acks.pop(tid, None)
        if event:
            event.result = "ack"
            event.set()

    def _on_nack(self, tid, app_uuid):
        event = self.acks.pop(tid, None)
        if event:
            event.result = "nack"
            event.set()

    def _on_message(self, tid, app_uuid, data):
        print("watch -> phone:", data)
        hello.update(data)

    def send(self, dictionary, timeout=10):
        event = threading.Event()
        event.result = None
        tid = self.service.send_message(APP_UUID, dictionary)
        self.acks[tid] = event
        started = time.time()
        if not event.wait(timeout):
            raise RuntimeError(f"no ack for transaction {tid}")
        return event.result, time.time() - started


def pack_test_pattern(width, height):
    stride = (width * 2 + 7) // 8
    data = bytearray(stride * height)
    for y in range(height):
        for x in range(width):
            band = x * 4 // width
            index = band
            if x == 1 or y == 1:
                index = 1
            if y % 20 == 0:
                index = 3
            data[y * stride + x // 4] |= index << (6 - 2 * (x % 4))
    return bytes(data)


def load_frame(path):
    with open(path, "rb") as f:
        header = f.read(4)
        width, height = struct.unpack("<HH", header)
        return width, height, f.read()


def send_frame(link, width, height, data, chunk_size, zoom, frame_id=1):
    total = len(data)
    offset = 0
    started = time.time()
    while offset < total:
        chunk = data[offset : offset + chunk_size]
        dictionary = {
            KEY_MAP_FRAME: Uint8(frame_id),
            KEY_MAP_OFFSET: Uint32(offset),
            KEY_MAP_DATA: ByteArray(chunk),
        }
        if offset == 0:
            dictionary[KEY_MAP_WIDTH] = Uint16(width)
            dictionary[KEY_MAP_HEIGHT] = Uint16(height)
            dictionary[KEY_MAP_TOTAL] = Uint32(total)
            dictionary[KEY_MAP_ZOOM] = Uint16(round(zoom * 100))
        result, elapsed = link.send(dictionary)
        print(f"chunk @{offset} len {len(chunk)} -> {result} in {elapsed:.2f}s")
        offset += len(chunk)
    print(f"frame {width}x{height}, {total} bytes in {time.time() - started:.2f}s")


def demo_arrow():
    size = 40
    rows = bytearray(5 * size)
    for y in range(size):
        for x in range(size):
            ink = (17 <= x <= 22 and y >= 10) or (abs(x - 20) <= (y - 4) and 4 <= y <= 16)
            if ink:
                rows[y * 5 + x // 8] |= 1 << (7 - x % 8)
    return bytes(rows)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--platform", default="emery")
    parser.add_argument("--demo", action="store_true", help="send a demo navigation state")
    parser.add_argument("--stop", action="store_true", help="send navigation stopped")
    parser.add_argument("--pattern", action="store_true", help="send a synthetic test frame")
    parser.add_argument("--frame", help="send a .wmf frame file (u16 w, u16 h, packed 2bpp rows)")
    parser.add_argument("--chunk", type=int, default=0, help="chunk size, default from watch hello")
    parser.add_argument("--zoom", type=float, default=16.0, help="zoom level stamped on the frame")
    parser.add_argument(
        "--launch",
        action="store_true",
        help="restart the watchapp first and wait for its hello",
    )
    args = parser.parse_args()

    link = Link(args.platform)
    time.sleep(1.5)
    if args.launch:
        link.pebble.send_packet(AppRunState(data=AppRunStateStop(uuid=APP_UUID)))
        time.sleep(1.5)
        link.pebble.send_packet(AppRunState(data=AppRunStateStart(uuid=APP_UUID)))
        time.sleep(3)
    if args.stop:
        print(link.send({KEY_NAV_ACTIVE: Uint8(0)}))
        return
    if args.demo:
        result = link.send(
            {
                KEY_NAV_ACTIVE: Uint8(1),
                KEY_MANEUVER: Uint8(2),
                KEY_ARROW_BITMAP: ByteArray(demo_arrow()),
                KEY_DISTANCE: CString("200 m"),
                KEY_STREET: CString("Rue de la Loi"),
                KEY_INSTRUCTION: CString("Turn left onto Rue de la Loi"),
                KEY_ETA: CString("10:45"),
                KEY_DIST_REMAIN: CString("2.1 km"),
                KEY_TIME_REMAIN: CString("14 min"),
            }
        )
        print("demo state ->", result)

    inbox_max = hello.get(41, 2048)
    chunk = args.chunk or max(200, min(inbox_max - 96, 8000))
    width = hello.get(42, 200)
    height = hello.get(43, 148)
    print("hello:", hello, "chunk:", chunk)

    if args.pattern:
        send_frame(link, width, height, pack_test_pattern(width, height), chunk, args.zoom)
    if args.frame:
        fw, fh, data = load_frame(args.frame)
        send_frame(link, fw, fh, data, chunk, args.zoom)


if __name__ == "__main__":
    main()

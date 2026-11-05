#!/usr/bin/env python3
"""Read every input line, summarize anonymous events; never export raw media paths."""
import collections
import hashlib
import re
import sys
from pathlib import Path

KEEP = {'scan_started','scan_cancelled','worker_started','adapter_commit','adapter_committed',
        'viewer_open','viewer_page_bound','media_viewer_publication','viewer_boundary_requested_folder',
        'viewer_catalog_miss','viewer_grid_order_handoff','viewer_catalog_lookup','viewer_catalog_upgrade',
        'media_scan_summary','media_sort_summary','media_source_summary','media_ui_summary','media_load_terminal',
        'viewer_full_list_prepared','viewer_full_list_ready','viewer_close','photo_catalog_ready'}
FIELDS = ['scopeKey','sourceType','businessRequest','itemCount','count','positionIndex','index','mediaId',
          'revision','terminalState','workerMs','metadataReadCount','metadataReadMs','mediaStoreRows','diskEnumerateMs',
          'sortCount','sortMs','sortMode','stageCount','stagePublishCount','diffMs']
for filename in sys.argv[1:]:
    p = Path(filename)
    raw = p.read_bytes()
    lines = raw.decode('utf-8-sig').splitlines()
    counts = collections.Counter()
    rows = []
    for number, line in enumerate(lines, 1):
        values = dict(re.findall(r'(\w+)=([^\s]+)', line))
        if 'event' not in values:
            continue
        counts[values['event']] += 1
        if values['event'] in KEEP:
            fields = ' '.join(f'{key}={values[key]}' for key in FIELDS if key in values)
            rows.append(f"| {number} | {line[:23]} | {values.get('elapsedRealtimeMs','—')} | {values.get('request','未提供')} | {values['event']} | {fields} |")
    print(f'\n### {p.name}\n\nUTF-8 bytes={len(raw)}；lines={len(lines)}；SHA-256 `{hashlib.sha256(raw).hexdigest()}`。')
    print('\n完整逐行事件计数：\n\n| Event | Count |\n|---|---:|')
    for event, count in sorted(counts.items()):
        print(f'| {event} | {count} |')
    print('\n保留全部关键事件（非采样；没有 request 的事件不能按时间强行归属）：\n')
    print('| 原行 | 日志墙钟 | 单调 ms | request | event | 匿名字段 |\n|---:|---|---:|---|---|---|')
    print('\n'.join(rows))

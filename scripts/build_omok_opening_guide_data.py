#!/usr/bin/env python3
import json
import sys
import zipfile
import xml.etree.ElementTree as ET
from collections import Counter, OrderedDict
from pathlib import Path

NS = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def load_shared_strings(zf: zipfile.ZipFile) -> list[str]:
    if "xl/sharedStrings.xml" not in zf.namelist():
        return []
    root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
    return [
        "".join((t.text or "") for t in si.findall(".//x:t", NS))
        for si in root.findall("x:si", NS)
    ]


def sheet_rows(zf: zipfile.ZipFile, name: str, shared: list[str]) -> list[dict[str, str]]:
    root = ET.fromstring(zf.read(name))
    rows = []
    for row in root.findall(".//x:sheetData/x:row", NS):
        data: dict[str, str] = {}
        for cell in row.findall("x:c", NS):
            ref = cell.attrib.get("r", "")
            col = "".join(ch for ch in ref if ch.isalpha())
            cell_type = cell.attrib.get("t")
            inline = cell.find("x:is", NS)
            value_node = cell.find("x:v", NS)
            value = ""
            if inline is not None:
                value = "".join((node.text or "") for node in inline.findall(".//x:t", NS))
            elif value_node is not None and value_node.text:
                value = value_node.text
                if cell_type == "s":
                    value = shared[int(value)]
            data[col] = value.strip()
        rows.append(data)
    return rows


def build_dataset(xlsx_path: Path) -> dict:
    with zipfile.ZipFile(xlsx_path) as zf:
        shared = load_shared_strings(zf)
        rows = sheet_rows(zf, "xl/worksheets/sheet2.xml", shared)

    entries = []
    counts = Counter()
    label_by_index: dict[int, str] = {}

    for row in rows:
        sequence = row.get("D", "")
        moves = [move for move in sequence.split() if move]
        if len(moves) != 5:
            continue

        eval_index = int(row.get("A", "0") or 0)
        eval_label = row.get("B", "")
        counts[eval_index] += 1
        label_by_index[eval_index] = eval_label

        entries.append(
            {
                "evalIndex": eval_index,
                "evalLabel": eval_label,
                "summary": row.get("C", ""),
                "sequence": moves,
                "page": int(row.get("E", "0") or 0),
                "catalog": int(row.get("F", "0") or 0),
                "title": row.get("G", ""),
                "sourceLabel": row.get("H", ""),
                "note": row.get("I", ""),
            }
        )

    prefix4: dict[str, list[dict]] = OrderedDict()
    prefix3_meta: dict[str, OrderedDict[str, dict]] = OrderedDict()

    for entry in entries:
        moves = entry["sequence"]
        p3 = " ".join(moves[:3])
        p4 = " ".join(moves[:4])
        fifth = moves[4]
        fourth = moves[3]

        prefix4.setdefault(p4, []).append(
            {
                "move": fifth,
                "evalIndex": entry["evalIndex"],
                "evalLabel": entry["evalLabel"],
                "summary": entry["summary"],
                "page": entry["page"],
                "catalog": entry["catalog"],
                "title": entry["title"],
                "sourceLabel": entry["sourceLabel"],
                "note": entry["note"],
            }
        )

        by_fourth = prefix3_meta.setdefault(p3, OrderedDict())
        if fourth not in by_fourth:
            by_fourth[fourth] = {
                "move": fourth,
                "bestEvalIndex": entry["evalIndex"],
                "bestEvalLabel": entry["evalLabel"],
                "candidateCount": 0,
            }
        by_fourth[fourth]["candidateCount"] += 1

    prefix4_ranked = {}
    for key, values in prefix4.items():
        ranked = []
        for rank, item in enumerate(values, start=1):
            ranked.append({"rank": rank, **item})
        prefix4_ranked[key] = ranked

    prefix3 = {
        key: list(values.values())
        for key, values in prefix3_meta.items()
    }

    legend = [
        {"index": index, "label": label_by_index.get(index, ""), "count": counts.get(index, 0)}
        for index in sorted(label_by_index)
    ]

    return {
        "meta": {
            "sourceFile": xlsx_path.name,
            "entryCount": len(entries),
            "prefix3Count": len(prefix3),
            "prefix4Count": len(prefix4_ranked),
            "coordinateSystem": {
                "origin": "A1",
                "xAxis": "right",
                "yAxis": "up",
                "center": "H8",
            },
        },
        "legend": legend,
        "prefix3": prefix3,
        "prefix4": prefix4_ranked,
    }


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: build_omok_opening_guide_data.py <input.xlsx> <output.json>", file=sys.stderr)
        return 1

    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    data = build_dataset(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""Busca un nodo en el volcado de uiautomator por texto o content-desc y devuelve el centro.
Uso: find_node.py ui.xml "texto" [last]  ·  prefijo "^" = empieza por, "~" = contiene."""
import re, sys
xml, query = sys.argv[1], sys.argv[2]
take_last = len(sys.argv) > 3 and sys.argv[3] == "last"
nodes = re.findall(r'<node [^>]*?(?:/>|>)', open(xml, encoding="utf-8").read())
hits = []
for n in nodes:
    text = re.search(r' text="([^"]*)"', n); desc = re.search(r' content-desc="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b: continue
    for v in (text.group(1) if text else "", desc.group(1) if desc else ""):
        v = v.replace("&amp;", "&")
        ok = (query[1:] in v) if query.startswith("~") else v.startswith(query[1:]) if query.startswith("^") else v == query
        if ok:
            x1, y1, x2, y2 = map(int, b.groups()); hits.append(((x1 + x2) // 2, (y1 + y2) // 2)); break
if not hits: sys.exit(1)
x, y = hits[-1] if take_last else hits[0]
print(x, y)

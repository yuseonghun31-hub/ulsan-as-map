"""동료용 지도 페이지 만들기 — index.html(형님 지도)을 바탕으로 담당 구역만 바꾼 u-<아이디>.html 을 만든다.

  python tools/make_page.py            → users/*.json 전부 다시 만듦
  (index.html 을 고친 뒤에는 꼭 한 번 실행해서 동료 지도에도 반영)

users/<아이디>.json 예시
  {"id": "ys", "label": "담당 구역",
   "areas": [["48", "양산시"], ["31", "울주군"], ["26", "북구"], ["26", "금정구"]]}
  · areas: [시도코드, geo/dong-<시도코드>.json 안의 시군구 이름]  (26 부산, 31 울산, 48 경남 …)
  · 시트 주소(API)는 여기 넣지 않는다 — 폰에서 '연결 코드'로 한 번 넣으면 그 폰에만 저장됨
"""
import glob
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLS = ["g-nam", "g-jung", "g-buk", "g-dong"]          # index.html 에 이미 있는 구역 색 4가지


def grab(s, name):
    key = "const " + name + "="
    i = s.index(key) + len(key)
    _, end = json.JSONDecoder().raw_decode(s[i:])
    return i, i + end


def put(s, name, obj):
    a, b = grab(s, name)
    return s[:a] + json.dumps(obj, ensure_ascii=False, separators=(",", ":")) + s[b:]


def once(s, old, new):
    assert s.count(old) == 1, (old[:80], s.count(old))
    return s.replace(old, new)


def sub_once(s, pat, new):
    s2, n = re.subn(pat, lambda m: new, s, count=1)
    assert n == 1, pat
    return s2


def box_of(dongs):
    xs, ys = [], []
    for d in dongs:
        nums = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", d["d"])]
        xs += nums[0::2]
        ys += nums[1::2]
    return [round(min(xs)), round(min(ys)), round(max(xs)), round(max(ys))]


def build(cfg, base):
    s = base
    pid = cfg["id"]
    assert re.fullmatch(r"[a-z0-9]{2,16}", pid)
    dongs, gus, skip = [], [], {}
    for k, g in cfg["areas"]:
        j = json.load(open(os.path.join(ROOT, "geo", f"dong-{k}.json"), encoding="utf-8"))
        got = [d for d in j["dongs"] if d["g"] == g]
        assert got, (k, g)
        dongs += got
        gus.append(g)
        skip.setdefault(k, []).append(g)
    assert len(gus) <= len(CLS), "구역은 4곳까지"
    extra = json.load(open(os.path.join(ROOT, "geo", "ulsan4.json"), encoding="utf-8"))   # 형님 지도에선 GEO가 그리던 울산 4개 구 외곽선

    # ① 시트 주소: 폰에 저장된 연결 코드에서
    s = sub_once(s, r'const API="[^"]*";', 'const API=(()=>{try{return localStorage.getItem("asmap.api")||""}catch(e){return ""}})();')
    # ② 동 경계·구역 이름·색
    s = put(s, "GEO", {"dongs": dongs, "box": box_of(dongs)})
    s = put(s, "L2A", {})
    s = sub_once(s, r"const GUS=\[[^\]]*\], GCLS=\{[^}]*\};",
                 "const GUS=" + json.dumps(gus, ensure_ascii=False) + ", GCLS="
                 + json.dumps({g: CLS[i] for i, g in enumerate(gus)}, ensure_ascii=False)
                 + ", SKIPD=" + json.dumps(skip, ensure_ascii=False) + ";")
    # ③ 주소 → 구역·동 (울산 전용 규칙 대신 담당 구역 이름으로)
    s = sub_once(s, r"function guOf\(g,addr\)\{[^\n]*\}\n",
                 'function guOf(g,addr){return String(g||"")}\n')   # 시트의 지역구 칸을 그대로 (주소로 추측하면 울산 북구가 부산 북구로 섞임)
    s = sub_once(s, r"function ldOf\(gu,addr,no\)\{[^\n]*\}\n",
                 'function ldOf(gu,addr,no){const a=String(addr||"");let m=a.match(/\\s([가-힣]+[읍면])\\s/);if(m)return m[1];'
                 'm=a.match(/\\(([가-힣]+\\d*[동리가])/);if(m)return m[1];return LD0[no]||""}\n')
    # 동 이름으로 위치 찾기: 같은 이름 → 이름이 들어간 동(신정동→신정1~5동) → 못 찾으면 구역 가운데
    s = sub_once(s, r"function locate\(gu,ld\)\{[^\n]*\}\n",
                 'function locate(gu,ld){const ds=GEO.dongs.filter(d=>d.g===gu);if(!ds.length)return null;'
                 'const st=String(ld||"").replace(/\\d*[동리가읍면]$/,"");'
                 'let cs=ds.filter(d=>d.n===ld);if(!cs.length&&st.length>=2)cs=ds.filter(d=>d.n.includes(st));if(!cs.length)cs=ds;'
                 'return [cs.reduce((s,d)=>s+d.c[0],0)/cs.length,cs.reduce((s,d)=>s+d.c[1],0)/cs.length]}\n')
    # ④ 겹쳐 그리지 않기: 확대 때 불러오는 동 경계·경남부산 시군구·전국 시군구에서 담당 구역은 빼고, 울산 4개 구 외곽선은 넣음
    s = once(s, 'NDONG[p.k]=j.dongs.filter(d=>!(p.k==="31"&&GUS.includes(d.g)));',
             'NDONG[p.k]=j.dongs.filter(d=>!(SKIPD[p.k]||[]).includes(d.g));')
    a, b = grab(s, "REGION")
    s = s[:b] + ";REGION.areas=REGION.areas.filter(a=>!GUS.includes(a.n))" + s[b:]
    a, b = grab(s, "NATION")
    s = (s[:b] + ";NATION.muni=NATION.muni.filter(m=>!(m.p===\"울산\"&&GUS.includes(m.n))).concat("
         + json.dumps([m for m in extra if m["n"] not in gus], ensure_ascii=False, separators=(",", ":")) + ")" + s[b:])
    # ⑤ 글자
    lab = cfg.get("label") or "담당 구역"
    s = once(s, 'ULAB.textContent="울산";', "ULAB.textContent=" + json.dumps(lab, ensure_ascii=False) + ";")
    s = once(s, '<button id="zul" class="zf" aria-label="울산만 보기">울산</button>',
             f'<button id="zul" class="zf" aria-label="{lab}만 보기">구역</button>')
    s = sub_once(s, r"PWHERE=\{[^}]*\};", 'PWHERE={HOME:"집",OFFICE:"사무실"};')
    s = once(s, 'aria-label="경남·부산 지도 위에 울산 남구·중구·북구·동구 동 경계와 방문 위치"',
             f'aria-label="지도 위에 {"·".join(gus)} 동 경계와 방문 위치"')
    s = s.replace("<!-- 형님 지도 -->", "")
    s = once(s, "<head>\n", f"<head>\n<!-- 자동 생성: tools/make_page.py users/{pid}.json — 직접 고치지 말고 index.html 을 고친 뒤 다시 만드세요 -->\n")
    return s


def main():
    base = open(os.path.join(ROOT, "index.html"), encoding="utf-8").read()
    for f in sorted(glob.glob(os.path.join(ROOT, "users", "*.json"))):
        cfg = json.load(open(f, encoding="utf-8"))
        out = os.path.join(ROOT, f"u-{cfg['id']}.html")
        open(out, "w", encoding="utf-8", newline="\n").write(build(cfg, base))
        print("만듦:", os.path.basename(out), "—", ", ".join(g for _, g in cfg["areas"]))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Detecta e remove travamentos (trechos congelados) do vídeo gravado pelo emulador.

Por que assim: o `screenrecord` do Android só emite quadro quando a tela muda, então
um travamento aparece como uma lacuna de tempo. O vídeo é normalizado para FPS constante
(os quadros repetidos viram "trecho congelado") e o filtro `freezedetect` do ffmpeg acha
esses trechos. Cada um é encurtado para `--keep` segundos e o resto é cortado.

Também pode checar o logcat: se o Lume deu ANR ou crash durante a gravação, sai com
código 3 (não adianta publicar um vídeo de um app quebrado).

Uso:
  python remove_freezes.py demo-raw.mp4 -o demo-clean.mp4 --report freeze-report.json
  python remove_freezes.py demo-raw.mp4 --dry-run          # só lista os travamentos
  python remove_freezes.py demo-raw.mp4 -o out.mp4 --logcat logcat.txt

Códigos de saída: 0 ok | 2 erro do ffmpeg | 3 ANR/crash no logcat | 5 vídeo curto demais
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile


def probe(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0",
         "-show_entries", "stream=width,height:format=duration", "-of", "json", path],
        capture_output=True, text=True, check=True).stdout
    d = json.loads(out)
    return float(d["format"]["duration"]), d["streams"][0]["width"], d["streams"][0]["height"]


def detect_freezes(path, duration, noise_db, min_freeze, fps):
    vf = f"fps={fps},freezedetect=n={noise_db}dB:d={min_freeze}"
    p = subprocess.run(
        ["ffmpeg", "-hide_banner", "-nostats", "-i", path, "-vf", vf, "-an", "-f", "null", "-"],
        capture_output=True, text=True)
    if p.returncode != 0:
        print(p.stderr[-3000:], file=sys.stderr)
        sys.exit(2)
    starts = [float(x) for x in re.findall(r"freeze_start: ([0-9.]+)", p.stderr)]
    ends = [float(x) for x in re.findall(r"freeze_end: ([0-9.]+)", p.stderr)]
    freezes = []
    for i, s in enumerate(starts):
        e = ends[i] if i < len(ends) else duration  # congelado até o fim do vídeo
        freezes.append((s, min(e, duration)))
    return freezes


def plan(duration, freezes, keep, min_cut):
    """Devolve (cortes, trechos_mantidos)."""
    cuts = []
    for s, e in freezes:
        a = s + keep
        if e - a >= min_cut:
            cuts.append((a, e))
    keeps, cur = [], 0.0
    for a, b in cuts:
        if a > cur:
            keeps.append((cur, a))
        cur = b
    if cur < duration - 0.01:
        keeps.append((cur, duration))
    return cuts, keeps


def render(path, out, keeps, fps, crf):
    expr = "+".join(f"gte(t,{a:.3f})*lt(t,{b:.3f})" for a, b in keeps)
    vf = f"fps={fps},select='{expr}',setpts=N/({fps}*TB)"
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
        f.write(vf)
        script = f.name
    try:
        p = subprocess.run(
            ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", path,
             "-filter_script:v", script, "-an",
             "-c:v", "libx264", "-preset", "veryfast", "-crf", str(crf),
             "-pix_fmt", "yuv420p", "-r", str(fps), "-movflags", "+faststart", out],
            capture_output=True, text=True)
    finally:
        os.unlink(script)
    if p.returncode != 0:
        print(p.stderr[-3000:], file=sys.stderr)
        sys.exit(2)


def check_logcat(path, pkg):
    problems = []
    lines = open(path, errors="replace").read().splitlines()
    for i, line in enumerate(lines):
        if re.search(rf"ANR in {re.escape(pkg)}", line):
            problems.append(("ANR", line.strip()))
        if "FATAL EXCEPTION" in line and any(f"Process: {pkg}" in x for x in lines[i + 1:i + 4]):
            problems.append(("CRASH", line.strip()))
    return problems


def main():
    ap = argparse.ArgumentParser(description="Remove travamentos do vídeo do emulador")
    ap.add_argument("input")
    ap.add_argument("-o", "--output", default="demo-clean.mp4")
    ap.add_argument("--noise", type=float, default=-60.0,
                    help="tolerância de diferença entre quadros em dB (padrão -60)")
    ap.add_argument("--min-freeze", type=float, default=0.8,
                    help="trecho parado a partir de N s conta como travamento (padrão 0.8)")
    ap.add_argument("--keep", type=float, default=0.3,
                    help="segundos de cada trecho parado que são mantidos (padrão 0.3)")
    ap.add_argument("--min-cut", type=float, default=0.4,
                    help="só corta se sobrar pelo menos N s para remover (padrão 0.4)")
    ap.add_argument("--min-output", type=float, default=20.0,
                    help="falha (código 5) se o vídeo final ficar menor que N s (padrão 20)")
    ap.add_argument("--fps", type=int, default=30)
    ap.add_argument("--crf", type=int, default=18)
    ap.add_argument("--logcat", help="logcat do emulador; falha (código 3) se houver ANR/crash")
    ap.add_argument("--package", default="com.clean.launcher")
    ap.add_argument("--report", help="grava um JSON com o que foi detectado/removido")
    ap.add_argument("--dry-run", action="store_true", help="só detecta, não gera vídeo")
    a = ap.parse_args()

    if not os.path.isfile(a.input):
        sys.exit(f"Arquivo não encontrado: {a.input}")

    if a.logcat and os.path.isfile(a.logcat):
        problems = check_logcat(a.logcat, a.package)
        if problems:
            print(f"✗ {len(problems)} problema(s) do app no logcat:")
            for kind, line in problems[:10]:
                print(f"  [{kind}] {line}")
            sys.exit(3)
        print("✓ logcat sem ANR/crash do app")

    duration, w, h = probe(a.input)
    print(f"Entrada: {w}x{h}, {duration:.1f}s")

    freezes = detect_freezes(a.input, duration, a.noise, a.min_freeze, a.fps)
    cuts, keeps = plan(duration, freezes, a.keep, a.min_cut)
    removed = sum(b - a_ for a_, b in cuts)
    final = duration - removed

    print(f"Travamentos detectados: {len(freezes)}")
    for s, e in freezes:
        print(f"  {s:7.2f}s → {e:7.2f}s  ({e - s:.2f}s parado)")
    print(f"A remover: {removed:.1f}s | vídeo final: {final:.1f}s")

    report = {
        "input_duration": round(duration, 3),
        "freezes": [{"start": round(s, 3), "end": round(e, 3)} for s, e in freezes],
        "cuts": [{"start": round(s, 3), "end": round(e, 3)} for s, e in cuts],
        "removed_seconds": round(removed, 3),
        "output_duration": round(final, 3),
    }
    if a.report:
        with open(a.report, "w") as f:
            json.dump(report, f, indent=2)

    if final < a.min_output:
        print(f"✗ vídeo final de {final:.1f}s é menor que {a.min_output:.0f}s: "
              "provavelmente o emulador travou de vez. Abortando.")
        sys.exit(5)

    if a.dry_run:
        print("[dry-run] nada foi gerado.")
        return

    render(a.input, a.output, keeps or [(0, duration)], a.fps, a.crf)
    out_dur, _, _ = probe(a.output)
    print(f"Gerado: {a.output} ({out_dur:.1f}s)")


if __name__ == "__main__":
    main()

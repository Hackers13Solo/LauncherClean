#!/usr/bin/env python3
"""
Edição automática do vídeo do Lume (ffmpeg).

- Compõe o vídeo vertical do celular num canvas 16:9 (1920x1080): fundo desfocado,
  celular centralizado com borda, "Lume" + tagline à esquerda, versão + link do APK à direita.
- Fade de entrada/saída.
- Trilha sonora: escolhe uma faixa da pasta --music-dir (baixe da YouTube Audio Library
  em studio.youtube.com → Biblioteca de áudio). A faixa muda a cada dia (rodízio),
  entra em loop se for mais curta que o vídeo, com fade in/out e volume normalizado.
- Opcional: gera a thumbnail (1280x720) a partir de um quadro do vídeo final.

Créditos: se uma faixa exigir atribuição, coloque uma linha no arquivo
<music-dir>/credits.txt no formato:   nome-do-arquivo.mp3 | texto da atribuição
Sem linha no credits.txt, o crédito vira "Música: <nome> (YouTube Audio Library)".

Uso:
  python edit_video.py demo-clean.mp4 -o final.mp4 --version 1.3.12 \
      --music-dir .github/demo/music --info edit-info.json --thumbnail thumb.jpg

Códigos de saída: 0 ok | 2 erro do ffmpeg | 4 sem música e --require-music ativo
"""
import argparse
import datetime
import json
import os
import subprocess
import sys
import tempfile

W, H = 1920, 1080
PHONE_H = 1000
AUDIO_EXT = {".mp3", ".m4a", ".wav", ".ogg", ".flac", ".aac"}
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
]


def probe(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0",
         "-show_entries", "stream=width,height:format=duration", "-of", "json", path],
        capture_output=True, text=True, check=True).stdout
    d = json.loads(out)
    return float(d["format"]["duration"]), d["streams"][0]["width"], d["streams"][0]["height"]


def find_font():
    for f in FONT_CANDIDATES:
        if os.path.isfile(f):
            return f
    sys.exit("Nenhuma fonte encontrada (instale fonts-dejavu-core).")


def pick_music(music_dir, music, seed):
    if music:
        return music
    if not music_dir or not os.path.isdir(music_dir):
        return None
    files = sorted(f for f in os.listdir(music_dir)
                   if os.path.splitext(f)[1].lower() in AUDIO_EXT)
    if not files:
        return None
    return os.path.join(music_dir, files[seed % len(files)])


def music_credit(music_path, music_dir):
    name = os.path.basename(music_path)
    credits = os.path.join(music_dir or os.path.dirname(music_path), "credits.txt")
    if os.path.isfile(credits):
        for line in open(credits, encoding="utf-8"):
            if "|" in line:
                fn, txt = line.split("|", 1)
                if fn.strip() == name and txt.strip():
                    return txt.strip()
    return f"Música: {os.path.splitext(name)[0]} (YouTube Audio Library)"


def write_text(tmp, name, text):
    p = os.path.join(tmp, name)
    with open(p, "w", encoding="utf-8") as f:
        f.write(text)
    return p


def build_filter(tmp, font, fg_w, version, repo_text, dur):
    x = (W - fg_w) // 2
    y = (H - PHONE_H) // 2
    left_cx = f"{x}/2-text_w/2"
    right_cx = f"{x + fg_w}+({W}-{x + fg_w})/2-text_w/2"
    fade_in = "alpha='min(1,max(0,(t-0.3)/0.8))'"

    def dt(fname, text, size, xexpr, yexpr, color="white"):
        tf = write_text(tmp, fname, text)
        return (f"drawtext=fontfile={font}:textfile={tf}:fontsize={size}:fontcolor={color}:"
                f"x={xexpr}:y={yexpr}:{fade_in}:shadowcolor=black@0.5:shadowx=2:shadowy=2")

    texts = ",".join([
        dt("t_title.txt", "Lume", 120, left_cx, f"{H}/2-170", "0xFFB547"),
        dt("t_l1.txt", "launcher Android", 40, left_cx, f"{H}/2-10"),
        dt("t_l2.txt", "limpo, animado e", 40, left_cx, f"{H}/2+45"),
        dt("t_l3.txt", "cheio de efeitos", 40, left_cx, f"{H}/2+100"),
        dt("t_ver.txt", f"v{version}", 90, right_cx, f"{H}/2-130", "0xFFB547"),
        dt("t_r1.txt", "Baixe o APK:", 38, right_cx, f"{H}/2+10"),
        dt("t_r2.txt", repo_text, 28, right_cx, f"{H}/2+65"),
    ])

    return (
        "[0:v]split=2[bgsrc][fgsrc];"
        f"[bgsrc]scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H},"
        "boxblur=40:5,eq=brightness=-0.12:saturation=1.1,"
        f"drawbox=x={x - 4}:y={y - 4}:w={fg_w + 8}:h={PHONE_H + 8}:color=white@0.35:t=fill[bg];"
        f"[fgsrc]scale={fg_w}:{PHONE_H}[fg];"
        f"[bg][fg]overlay={x}:{y},{texts},"
        f"fade=t=in:st=0:d=0.6,fade=t=out:st={max(dur - 1.0, 0):.2f}:d=1.0,format=yuv420p[v]"
    )


def main():
    ap = argparse.ArgumentParser(description="Edição automática do vídeo do Lume")
    ap.add_argument("input")
    ap.add_argument("-o", "--output", default="final.mp4")
    ap.add_argument("--version", required=True, help="ex: 1.3.12 (sem o 'v')")
    ap.add_argument("--music-dir", default=".github/demo/music")
    ap.add_argument("--music", help="usar esta faixa específica (ignora o rodízio)")
    ap.add_argument("--seed", type=int, default=datetime.date.today().toordinal(),
                    help="define a faixa do rodízio (padrão: dia atual)")
    ap.add_argument("--require-music", action="store_true",
                    help="falha (código 4) se não houver nenhuma faixa de música")
    ap.add_argument("--repo-text", default="github.com/mafiizz13exe/LauncherClean")
    ap.add_argument("--info", help="grava JSON com faixa/crédito/duração")
    ap.add_argument("--thumbnail", help="gera a thumbnail (jpg) neste caminho")
    ap.add_argument("--crf", type=int, default=20)
    a = ap.parse_args()

    if not os.path.isfile(a.input):
        sys.exit(f"Arquivo não encontrado: {a.input}")

    version = a.version.lstrip("vV")
    dur, w, h = probe(a.input)
    fg_w = round(PHONE_H * w / h / 2) * 2
    font = find_font()

    track = pick_music(a.music_dir, a.music, a.seed)
    if track is None:
        if a.require_music:
            print(f"✗ Nenhuma faixa em '{a.music_dir}'. Baixe músicas da YouTube Audio Library "
                  "e coloque lá (mp3/m4a/wav/ogg/flac).")
            sys.exit(4)
        print("⚠ Sem música: o vídeo sai com trilha em silêncio.")

    with tempfile.TemporaryDirectory() as tmp:
        fc = build_filter(tmp, font, fg_w, version, a.repo_text, dur)
        cmd = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", a.input]
        if track:
            cmd += ["-stream_loop", "-1", "-i", track]
            af = (f"[1:a]atrim=0:{dur:.3f},asetpts=PTS-STARTPTS,"
                  f"afade=t=in:st=0:d=1.5,afade=t=out:st={max(dur - 3, 0):.2f}:d=3,"
                  "loudnorm=I=-16:TP=-1.5:LRA=11,aresample=44100[a]")
        else:
            cmd += ["-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo"]
            af = f"[1:a]atrim=0:{dur:.3f},asetpts=PTS-STARTPTS[a]"
        cmd += ["-filter_complex", f"{fc};{af}", "-map", "[v]", "-map", "[a]",
                "-c:v", "libx264", "-preset", "medium", "-crf", str(a.crf), "-r", "30",
                "-c:a", "aac", "-b:a", "192k", "-t", f"{dur:.3f}",
                "-movflags", "+faststart", a.output]
        p = subprocess.run(cmd, capture_output=True, text=True)
        if p.returncode != 0:
            print(p.stderr[-4000:], file=sys.stderr)
            sys.exit(2)

    out_dur, _, _ = probe(a.output)
    print(f"Gerado: {a.output} ({out_dur:.1f}s, 1920x1080)")

    credit = music_credit(track, a.music_dir) if track else ""
    if track:
        print(f"Música: {os.path.basename(track)}")

    if a.thumbnail:
        t = max(out_dur * 0.35, 1.0)
        p = subprocess.run(
            ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-ss", f"{t:.2f}",
             "-i", a.output, "-frames:v", "1", "-vf", "scale=1280:720", "-q:v", "2", a.thumbnail],
            capture_output=True, text=True)
        if p.returncode == 0:
            print(f"Thumbnail: {a.thumbnail}")
        else:
            print("⚠ Não foi possível gerar a thumbnail.", file=sys.stderr)

    if a.info:
        with open(a.info, "w", encoding="utf-8") as f:
            json.dump({
                "version": version,
                "duration": round(out_dur, 3),
                "music_file": os.path.basename(track) if track else None,
                "music_credit": credit,
            }, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()

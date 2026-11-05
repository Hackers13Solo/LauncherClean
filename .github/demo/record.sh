#!/usr/bin/env bash
# Roda dentro do emulador (android-emulator-runner). Instala o Lume, grava a tela e simula o uso.
set -u
APK="${1:-Lume.apk}"
OUT="${2:-demo-raw.mp4}"
PKG=com.clean.launcher

# toca no centro do 1º elemento cujo texto/descrição contém $1 (via uiautomator)
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  python3 - "$1" <<'PY'
import re, sys, subprocess
import xml.etree.ElementTree as ET
alvo = sys.argv[1].lower()
try:
    root = ET.parse("ui.xml").getroot()
except Exception:
    sys.exit(1)
for n in root.iter("node"):
    t = (n.get("text") or "") + " " + (n.get("content-desc") or "")
    if alvo in t.lower():
        m = re.findall(r"\d+", n.get("bounds"))
        x = (int(m[0]) + int(m[2])) // 2
        y = (int(m[1]) + int(m[3])) // 2
        subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])
        sys.exit(0)
sys.exit(1)
PY
}

mkdir -p snaps
snap() { adb exec-out screencap -p > "snaps/$1.png" 2>/dev/null; adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb pull /sdcard/u.xml "snaps/$1.xml" >/dev/null 2>&1; }

W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
echo "Tela: ${W}x${H}"

adb install -r "$APK"
adb shell cmd package set-home-activity "$PKG/.MainActivity" || true
adb shell settings put system pointer_location 0
adb shell settings put system show_touches 1   # mostra os toques no vídeo
adb shell input keyevent KEYCODE_HOME
sleep 4

# --- começa a gravar (limite do screenrecord: 180 s) ---
adb shell "screenrecord --bit-rate 12000000 --time-limit 170 /sdcard/demo.mp4" &
REC=$!
sleep 3

# --- boas-vindas animadas ---
sleep 4
snap 00_boas_vindas
tap_text "Vamos começar"; sleep 3
# nome: foca o campo, digita e continua (sem BACK, que volta uma etapa)
tap_text "Seu nome" || adb shell input tap $((W/2)) $((H/5))
sleep 1.5
adb shell input text "Visitante"; sleep 2
snap 01_nome
tap_text "Continuar"; sleep 3.5
snap 02_apos_nome
for i in $(seq 1 14); do
  if tap_text "Entrar no Lume"; then break; fi
  case $i in
    1) tap_text "Grade" || true; sleep 2; tap_text "Compacto" || true; sleep 2; tap_text "Clássico" || true; sleep 1.5 ;;
  esac
  snap "03_etapa_$i"
  tap_text "Continuar" || tap_text "Vamos começar" || true
  sleep 3.5
done
sleep 5
snap 04_entrou

# --- tela inicial ---
adb shell input keyevent KEYCODE_HOME; sleep 3
snap 05_home

# rolagem pela lista de apps
for i in 1 2 3; do
  adb shell input swipe $((W/2)) $((H*7/10)) $((W/2)) $((H*3/10)) 450; sleep 1.5
done
for i in 1 2 3; do
  adb shell input swipe $((W/2)) $((H*3/10)) $((W/2)) $((H*7/10)) 450; sleep 1.2
done

# arrasta pela barra alfabética A–Z (borda direita)
adb shell input swipe $((W-14)) $((H*2/10)) $((W-14)) $((H*8/10)) 2500; sleep 2
adb shell input swipe $((W-14)) $((H*8/10)) $((W-14)) $((H*2/10)) 2500; sleep 2

# busca com calculadora
tap_text "Buscar" || adb shell input tap $((W/2)) $((H/10))
sleep 2
snap 06_busca
adb shell "input text '12*3+4'"; sleep 4
snap 07_calculo
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text "Buscar" || true; sleep 1.5
adb shell input text "chrome"; sleep 3
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2

# abre um app e volta para a tela inicial
adb shell input keyevent KEYCODE_HOME; sleep 4

# --- para a gravação ---
adb shell pkill -2 screenrecord || true
wait $REC 2>/dev/null || true
sleep 3
adb pull /sdcard/demo.mp4 "$OUT"
ls -la "$OUT"

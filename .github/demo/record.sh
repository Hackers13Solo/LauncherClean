#!/usr/bin/env bash
# Roda dentro do emulador (android-emulator-runner). Instala o Lume, grava a tela e simula o uso.
# Toques por coordenadas (em milésimos da tela), medidas nas capturas reais do app.
set -u
APK="${1:-Lume.apk}"
OUT="${2:-demo-raw.mp4}"
PKG=com.clean.launcher

mkdir -p snaps
snap() { adb exec-out screencap -p > "snaps/$1.png" 2>/dev/null; }

SZ=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SZ%x*}; H=${SZ#*x}
echo "Tela: ${W}x${H}"
tapf() { adb shell input tap $(( W * $1 / 1000 )) $(( H * $2 / 1000 )); }
swipef() { adb shell input swipe $(( W * $1 / 1000 )) $(( H * $2 / 1000 )) $(( W * $3 / 1000 )) $(( H * $4 / 1000 )) $5; }
hide_kb() { adb shell dumpsys input_method | grep -q 'mInputShown=true' && adb shell input keyevent KEYCODE_BACK; }

BTN_X=500; BTN_Y=922     # botão principal do rodapé (Vamos começar / Continuar / Entrar no Lume)

adb install -r "$APK"
adb shell cmd package set-home-activity "$PKG/.MainActivity" || true
adb shell settings put system show_touches 1
adb shell input keyevent KEYCODE_HOME
sleep 5

# --- começa a gravar (limite do screenrecord: 180 s) ---
adb shell "screenrecord --bit-rate 12000000 --time-limit 178 /sdcard/demo.mp4" &
REC=$!
sleep 6
snap 00_boas_vindas

# Etapa 0: boas-vindas
tapf $BTN_X $BTN_Y; sleep 2.6

# Etapa 1: nome (teclas, mais confiável que "input text"); com o teclado aberto o botão sobe
tapf 500 295; sleep 1.2
adb shell input keyevent KEYCODE_A KEYCODE_N KEYCODE_A; sleep 1.5
snap 01_nome
tapf 500 583; sleep 2.6

# Etapa 2: layout (Grade e volta ao Clássico)
tapf 630 375; sleep 1.6
tapf 240 375; sleep 1.0
tapf $BTN_X $BTN_Y; sleep 2.6

# Etapa 3: cor de destaque
tapf 310 258; sleep 0.9
tapf 310 335; sleep 0.9
tapf 480 335; sleep 0.9
tapf 650 335; sleep 1.2
tapf $BTN_X $BTN_Y; sleep 2.6

# Etapa 4: efeitos (liga Neve e Aurora)
tapf 840 375; sleep 1.2
tapf 840 565; sleep 1.8
snap 04_efeitos
tapf $BTN_X $BTN_Y; sleep 2.6

# Etapa 5: papel de parede (pula) | Etapa 6: últimos ajustes
tapf $BTN_X $BTN_Y; sleep 2.0
tapf $BTN_X $BTN_Y; sleep 3.2     # chega na etapa final (confete)
snap 05_confete
tapf $BTN_X $BTN_Y; sleep 4       # "Entrar no Lume" -> tela inicial
snap 06_home

# rolagem pela lista de apps
for i in 1 2; do swipef 500 700 500 300 450; sleep 1.2; done
for i in 1 2; do swipef 500 300 500 700 450; sleep 1.0; done

# barra alfabética A–Z (borda direita): desce e sobe
swipef 950 575 950 860 2200; sleep 1.2
snap 07_az
swipef 950 860 950 575 2200; sleep 1.2

# busca com calculadora
tapf 878 917; sleep 2.2
adb shell "input text '12*3+4'"; sleep 3.5
snap 08_calculo
adb shell input keyevent KEYCODE_BACK; sleep 0.8
adb shell input keyevent KEYCODE_HOME; sleep 2.5

# ajustes (engrenagem)
tapf 717 917; sleep 3
snap 09_ajustes
swipef 500 700 500 300 500; sleep 1.5
swipef 500 300 500 700 500; sleep 1.5
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 3
snap 10_final

# --- para a gravação ---
adb shell pkill -2 screenrecord || true
wait $REC 2>/dev/null || true
sleep 3
adb pull /sdcard/demo.mp4 "$OUT"
ls -la "$OUT"

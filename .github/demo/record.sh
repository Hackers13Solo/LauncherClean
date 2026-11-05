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
adb shell "screenrecord --bit-rate 12000000 --time-limit 170 /sdcard/demo.mp4" &
REC=$!
sleep 6
snap 00_boas_vindas

# Etapa 0: boas-vindas
tapf $BTN_X $BTN_Y; sleep 3.2

# Etapa 1: nome
tapf 500 295; sleep 1.5
adb shell input text "Visitante"; sleep 2
snap 01_nome
hide_kb; sleep 1
tapf $BTN_X $BTN_Y; sleep 3.2

# Etapa 2: layout (toca em Grade e volta ao Clássico)
tapf 630 375; sleep 2
snap 02_layout
tapf 240 375; sleep 1.5
tapf $BTN_X $BTN_Y; sleep 3.2

# Etapa 3: cor de destaque e formato dos ícones
tapf 310 258; sleep 1.3     # laranja
tapf 310 335; sleep 1.3     # azul
tapf 480 335; sleep 1.3     # roxo
tapf 650 335; sleep 1.5     # rosa
snap 03_cor
tapf $BTN_X $BTN_Y; sleep 3.2

# Etapa 4: efeitos de fundo (liga Neve e Aurora)
tapf 840 375; sleep 1.5
tapf 840 565; sleep 2
snap 04_efeitos
tapf $BTN_X $BTN_Y; sleep 3.2

# Etapa 5: papel de parede (pula)  |  Etapa 6: últimos ajustes
tapf $BTN_X $BTN_Y; sleep 2.5
tapf $BTN_X $BTN_Y; sleep 2.5
snap 05_ultimos
# Etapa 7: final
tapf $BTN_X $BTN_Y; sleep 3
snap 06_confete
tapf $BTN_X $BTN_Y; sleep 5

# --- tela inicial ---
adb shell input keyevent KEYCODE_HOME; sleep 3
snap 07_home

# rolagem pela lista de apps
for i in 1 2 3; do swipef 500 700 500 300 450; sleep 1.4; done
snap 08_lista
for i in 1 2 3; do swipef 500 300 500 700 450; sleep 1.2; done

# barra alfabética A–Z (borda direita): desce e sobe
swipef 950 575 950 860 2500; sleep 1.5
snap 09_az
swipef 950 860 950 575 2500; sleep 1.5

# busca com calculadora
tapf 878 917; sleep 2.5
snap 10_busca
adb shell "input text '12*3+4'"; sleep 4
snap 11_calculo
adb shell input keyevent KEYCODE_DEL; adb shell input keyevent KEYCODE_DEL
adb shell input keyevent KEYCODE_DEL; adb shell input keyevent KEYCODE_DEL
adb shell input keyevent KEYCODE_DEL; adb shell input keyevent KEYCODE_DEL
adb shell input text "chrome"; sleep 3
snap 12_busca_app
hide_kb; sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 3

# ajustes (engrenagem)
tapf 717 917; sleep 3
snap 13_ajustes
swipef 500 700 500 300 500; sleep 1.5
swipef 500 300 500 700 500; sleep 1.5
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 4
snap 14_final

# --- para a gravação ---
adb shell pkill -2 screenrecord || true
wait $REC 2>/dev/null || true
sleep 3
adb pull /sdcard/demo.mp4 "$OUT"
ls -la "$OUT"

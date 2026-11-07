#!/usr/bin/env bash
# Grava o Lume no emulador em CLIPES SEPARADOS (um por cena), para editar cada cena com calma.
# Toques por coordenadas (milésimos da tela), medidas em capturas reais do app.
set -u
APK="${1:-Lume.apk}"
PKG=com.clean.launcher
mkdir -p snaps clips

snap() { adb exec-out screencap -p > "snaps/$1.png" 2>/dev/null; }
SZ=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SZ%x*}; H=${SZ#*x}
echo "Tela: ${W}x${H}"
tapf()   { adb shell input tap $(( W * $1 / 1000 )) $(( H * $2 / 1000 )); }
swipef() { adb shell input swipe $(( W * $1 / 1000 )) $(( H * $2 / 1000 )) $(( W * $3 / 1000 )) $(( H * $4 / 1000 )) $5; }
longf()  { adb shell input swipe $(( W * $1 / 1000 )) $(( H * $2 / 1000 )) $(( W * $1 / 1000 )) $(( H * $2 / 1000 )) 900; }
# toca num nó da tela pelo atributo (text ou content-desc); sem achar, usa as coordenadas de reserva
tap_attr() {  # tap_attr text|content-desc "valor" x y
  rm -f ui.xml; timeout 25 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  C=$(python3 - "$1" "$2" <<'PY'
import re, sys
a, t = sys.argv[1], sys.argv[2]
try: x = open('ui.xml', encoding='utf-8').read()
except Exception: sys.exit()
m = re.search(r'%s="%s"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % (re.escape(a), re.escape(t)), x)
if m:
    p, q, r, s = map(int, m.groups()); print((p + r) // 2, (q + s) // 2)
PY
)
  if [ -n "$C" ]; then echo "tap $1='$2' em $C"; adb shell input tap $C; else echo "tap $1='$2' sem uiautomator: coordenadas"; tapf $3 $4; fi
}
tap_text() { tap_attr text "$1" $2 $3; }
tap_desc() { tap_attr content-desc "$1" $2 $3; }

BTN_X=500; BTN_Y=922
RECPID=""
rec_start() { adb shell "screenrecord --bit-rate 12000000 --time-limit 170 /sdcard/$1.mp4" & RECPID=$!; sleep 2.5; }
rec_stop()  { adb shell pkill -2 screenrecord || true; wait $RECPID 2>/dev/null || true; sleep 3; adb pull /sdcard/$1.mp4 "clips/$1.mp4" >/dev/null; ls -la "clips/$1.mp4"; }

adb install -r "$APK"
adb shell cmd package set-home-activity "$PKG/.MainActivity" || true
# serviço de acessibilidade do Lume ligado (para a tela dividida do modo carro)
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.SplitService" || true
adb shell settings put secure accessibility_enabled 1 || true
adb shell settings put system show_touches 1
adb shell input keyevent KEYCODE_HOME
sleep 5

# ============ 1) boas-vindas ============
rec_start 01_boas_vindas
snap 00_boas_vindas
tapf $BTN_X $BTN_Y; sleep 2.6
tapf 500 295; sleep 1.2
adb shell input keyevent KEYCODE_A KEYCODE_N KEYCODE_A; sleep 1.5
snap 01_nome
tapf 500 583; sleep 2.6
tapf 630 375; sleep 1.6          # layout Grade
tapf 240 375; sleep 1.0          # volta ao Clássico
tapf $BTN_X $BTN_Y; sleep 2.6
tapf 310 258; sleep 0.9          # cores
tapf 310 335; sleep 0.9
tapf 480 335; sleep 0.9
tapf 650 335; sleep 1.2
tapf $BTN_X $BTN_Y; sleep 2.6
tapf 840 375; sleep 1.2          # efeitos: neve e aurora
tapf 840 565; sleep 1.8
snap 04_efeitos
tapf $BTN_X $BTN_Y; sleep 2.6
tapf $BTN_X $BTN_Y; sleep 2.0
tapf $BTN_X $BTN_Y; sleep 3.2
snap 05_confete
tapf $BTN_X $BTN_Y; sleep 4
snap 06_home
rec_stop 01_boas_vindas

# ============ 2) lista de apps e barra A–Z ============
rec_start 02_lista
for i in 1 2; do swipef 500 700 500 300 450; sleep 1.2; done
for i in 1 2; do swipef 500 300 500 700 450; sleep 1.0; done
swipef 950 575 950 860 2200; sleep 1.2
snap 07_az
swipef 950 860 950 575 2200; sleep 1.2
adb shell input keyevent KEYCODE_HOME; sleep 2.5
rec_stop 02_lista

# ============ 3) favoritos e menu do app ============
rec_start 03_favoritos
snap 07b_antes_fav
longf 400 800; sleep 1.8
snap 07c_menu
tap_text "Favoritar" 500 330; sleep 2.4
snap 07d_depois_fav
longf 400 842; sleep 1.8
snap 07e_menu2
tap_text "Favoritar" 500 330; sleep 2.4
snap 07f_dois_fav
swipef 500 700 500 400 500; sleep 1.5
swipef 500 400 500 700 500; sleep 1.5
adb shell input keyevent KEYCODE_HOME; sleep 2
rec_stop 03_favoritos

# ============ 4) busca e calculadora ============
rec_start 04_busca
tapf 878 917; sleep 2.2
adb shell "input text '12*3+4'"; sleep 3.5
snap 08_calculo
adb shell input keyevent KEYCODE_BACK; sleep 0.8
adb shell input keyevent KEYCODE_HOME; sleep 2.5
rec_stop 04_busca

# ============ 5) ajustes por categoria ============
rec_start 05_ajustes
tapf 717 917; sleep 3
snap 09_ajustes
# posições (milésimos de altura) das categorias na lista de ajustes
for Y in 173 379 482 585 688 894; do
  tapf 500 $Y; sleep 2.4
  snap "ajuste_$Y"
  swipef 500 700 500 350 450; sleep 1.0
  swipef 500 350 500 700 450; sleep 0.8
  adb shell input keyevent KEYCODE_BACK; sleep 1.4
done
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 2.5
rec_stop 05_ajustes

# ============ 6) modo carro: YouTube em cima, GPS embaixo ============
rec_start 06_carro
snap 10_home_carro
tap_desc "Modo carro" 889 476; sleep 2.5
snap 11_carro_escolha
tap_text "Maps" 500 500; sleep 3
snap 12_carro_1
sleep 4; snap 13_carro_2
sleep 4; snap 14_carro_3
sleep 4; snap 15_carro_4
rec_stop 06_carro

adb logcat -d -v time > logcat.txt || true

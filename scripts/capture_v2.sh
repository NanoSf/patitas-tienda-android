#!/usr/bin/env bash
# Recorre Patitas v2 en el emulador, conectada al Firebase real, y guarda capturas en shots/.
# Orden: transversal → Vendedor → Administrador → Comprador (con GPS y checkout de ePayco).
set -u
mkdir -p shots
PKG=co.patitas.tienda
APK=${APK:-Patitas.apk}
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1; }
shot() { sleep ${2:-1.5}; adb exec-out screencap -p > "shots/$1.png"; echo "captura $1"; }
dismiss_anr() {
  if python3 scripts/find_node.py /tmp/ui.xml "Wait" >/dev/null 2>&1; then
    adb shell input tap $(python3 scripts/find_node.py /tmp/ui.xml "Wait"); sleep 1; dump
  fi
}
tap() {
  for i in 1 2 3 4 5 6 7 8; do
    dump; dismiss_anr
    if pos=$(python3 scripts/find_node.py /tmp/ui.xml "$1" "${2:-}"); then
      adb shell input tap $pos; sleep 1.5; return 0
    fi
    sleep 1.5
  done
  echo "no encontrado: $1"; adb exec-out screencap -p > "shots/zz_error_$(date +%s).png"; return 1
}
has() { dump; python3 scripts/find_node.py /tmp/ui.xml "$1" >/dev/null 2>&1; }
type_in() { tap "#$1"; adb shell input text "$2"; sleep 0.5; }
back() { adb shell input keyevent 4; sleep 1.2; }
swipe_up() { adb shell input swipe 540 1800 540 700 400; sleep 1; }
login() { tap "$1"; tap "Iniciar sesión" last; sleep 6; }
logout() { tap "Abrir menú"; tap "Cerrar sesión"; tap "Cerrar sesión" last; sleep 2; tap "Iniciar sesión"; }

adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
sleep 5
adb install -r "$APK"
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb shell settings put secure location_mode 3
adb emu geo fix -74.0636 4.6486    # Bogotá, Chapinero (longitud, latitud)
sleep 10
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null

# ---------- Transversal
adb shell am start -W -n $PKG/.ui.auth.SplashActivity >/dev/null
sleep 0.9; adb exec-out screencap -p > shots/t01_splash.png
sleep 4; shot t02_bienvenida
tap "Crear cuenta"; shot t05_registro
tap "Quiero vender"; shot t06_registro_vendedor
back
tap "Iniciar sesión"; shot t03_login
tap "¿Olvidaste tu contraseña?"; shot t07_recuperar
type_in inputEmail "comprador@patitas.co"; adb shell input keyevent 111
tap "Enviar enlace"; shot t08_recuperar_enviado 3
back
tap "Comprador"; shot t04_login_demo

# ---------- Vendedor (el primer ingreso crea la cuenta y los productos de ejemplo)
login "Vendedor"; shot v01_panel 3
tap "Abrir menú"; shot v00_drawer_vendedor; back
tap "Productos"; shot v02_productos 3
tap "Más acciones"; shot v03_menu_acciones; back
tap "Nuevo producto" last; shot v04_form_producto
type_in inputName "Comedero doble en acero inoxidable"
type_in inputBrand "Mundo Animal"
type_in inputPresentation "2 x 750 ml"
swipe_up
type_in inputPrice "38900"
type_in inputStock "12"
adb shell input keyevent 111
shot v05_form_lleno
tap "Galería"; sleep 4; shot v06_galeria
back
tap "Guardar borrador" || tap "~borrador"
shot v07_productos_borrador 3
tap "Pedidos"; shot v08_pedidos 3
if has "^Pedido PT-"; then tap "^Pedido PT-"; shot v09_detalle_pedido; back; fi
tap "Mi tienda"; shot v10_perfil_tienda 3
logout

# ---------- Administrador
login "Administrador"; shot a01_dashboard 4
swipe_up; shot a02_dashboard_actividad
tap "Abrir menú"; shot a00_drawer_admin; back
tap "Usuarios"; shot a03_usuarios 3
tap "Más acciones"; shot a04_menu_usuario; back
tap "Nuevo usuario" last; shot a05_form_usuario; back
tap "Productos"; shot a06_moderacion 3
if has "Aprobar"; then tap "Aprobar"; shot a07_aprobado 2; fi
tap "Reportes"; shot a08_reporte 3
swipe_up; shot a09_reporte_categorias
logout

# ---------- Comprador
login "Comprador"; shot c01_inicio 4
tap "Abrir menú"; shot c00_drawer_comprador; back
tap "Catálogo"; shot c02_catalogo 3
tap "~Concentrado NutriCan"; shot c03_detalle 3
tap "^Agregar ·"; sleep 2; back
tap "~Rascador torre"; tap "^Agregar ·"; sleep 2; back
tap "Carrito"; shot c04_carrito 3
tap "Continuar con la entrega"; shot c05_entrega
tap "Usar mi ubicación actual (GPS)"; shot c06_entrega_gps 8
tap "^Ir a pagar"; shot c07_pago 3
tap "^Pagar "; sleep 15; shot c08_epayco 3
swipe_up; shot c09_epayco_metodos
back; tap "Salir del pago" || true; back
tap "Pedidos"; shot c10_pedidos 3
if has "^Pedido PT-"; then tap "^Pedido PT-"; shot c11_detalle_pedido; back; fi
tap "Perfil"; shot c12_perfil 3
echo "Listo: $(ls shots | wc -l) capturas"

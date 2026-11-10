#!/usr/bin/env bash
# Recorre las pantallas de Patitas en el emulador y guarda capturas en shots/.
set -u
mkdir -p shots
PKG=co.patitas.tienda
shot() { sleep 1.2; adb exec-out screencap -p > "shots/$1.png"; echo "captura $1"; }
tap() {
  for i in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    if pos=$(python3 scripts/find_node.py /tmp/ui.xml "$1" "${2:-}"); then
      adb shell input tap $pos; sleep 1.5; return 0
    fi
    sleep 1.5
  done
  echo "no encontrado: $1"; adb exec-out screencap -p > "shots/zz_error_$(date +%s).png"; return 1
}
back() { adb shell input keyevent 4; sleep 1.2; }
login() { tap "Iniciar sesión"; tap "$1"; tap "Iniciar sesión" last; sleep 2; }
logout() { tap "Abrir menú"; tap "Cerrar sesión"; tap "Cerrar sesión" last; sleep 2; }

adb install -r Patitas_v1.0.0.apk
# Barra de estado limpia (modo demostración)
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null

# ---------- Transversal
adb shell am start -W -n $PKG/.ui.auth.SplashActivity >/dev/null
sleep 0.9; adb exec-out screencap -p > shots/t01_splash.png
sleep 3; shot t02_bienvenida
tap "Crear cuenta"; shot t05_registro
tap "Quiero vender"; shot t06_registro_vendedor
back; back
tap "Iniciar sesión"; shot t03_login
tap "¿Olvidaste tu contraseña?"; shot t07_recuperar
back
tap "Comprador"; shot t04_login_demo
tap "Iniciar sesión" last; sleep 2

# ---------- Comprador
shot c01_inicio
tap "Abrir menú"; shot t08_drawer_comprador; back
tap "Catálogo"; shot c02_catalogo
tap "~Concentrado NutriCan"; shot c03_detalle
tap "^Agregar ·"; sleep 1; back
tap "~Rascador torre"; tap "^Agregar ·"; back
tap "Carrito"; shot c04_carrito
tap "Continuar con la entrega"; tap "Usar mi ubicación actual (GPS)"; shot c05_entrega
tap "^Ir a pagar"; shot c06_pago
tap "PSE"; shot c07_pago_pse
back; back
tap "Pedidos"; shot c08_pedidos
tap "Perfil"; shot c09_perfil
logout

# ---------- Vendedor
login "Vendedor"; shot v01_panel
tap "Abrir menú"; shot t09_drawer_vendedor; back
tap "Productos"; shot v02_productos
tap "Más acciones"; shot v03_menu_acciones; back
tap "Nuevo producto" last; shot v04_form_producto; back
tap "Pedidos"; shot v05_pedidos
tap "~PT-1049"; shot v06_detalle_pedido; back
tap "Mi tienda"; shot v07_perfil_tienda
logout

# ---------- Administrador
login "Administrador"; shot a01_dashboard
tap "Abrir menú"; shot t10_drawer_admin; back
tap "Usuarios"; shot a02_usuarios
tap "Más acciones"; shot a03_menu_usuario; back
tap "Nuevo usuario" last; shot a04_form_usuario; back
tap "~Andrés Pérez"; shot a05_editar_usuario; back
tap "Productos"; shot a06_productos
tap "Reportes"; shot a07_reporte
adb shell input swipe 540 1700 540 700 400; shot a08_reporte_categorias
echo "Listo: $(ls shots | wc -l) capturas"

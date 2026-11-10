# Patitas · Tienda virtual Android para mascotas

Aplicación nativa Android (Kotlin + vistas XML con Material Components) de un *marketplace* de productos para mascotas: alimento, snacks, rascadores, camas, ropa y juguetes.
Proyecto académico de **Desarrollo de Aplicaciones Móviles Nativas** — Fundación Universitaria Compensar (UCompensar).
Autor: **Einer Fabian Aponte Cubides**.

🎥 **Video explicativo:** https://youtu.be/znGFyRGXpa0  
📦 **APK:** [Releases v1.0.0](https://github.com/NanoSf/patitas-tienda-android/releases/tag/v1.0.0)

## Alcance de esta entrega (Actividad 3: frontend)

Interfaces gráficas completas para los tres roles definidos en la Actividad 2. Los datos son de demostración, en memoria (`PatitasRepository`). La integración con Firebase y Wompi corresponde a la siguiente fase.

| Rol | Pantallas |
|---|---|
| Transversal | Splash, bienvenida, inicio de sesión con **huella (BiometricPrompt)**, registro, recuperación de contraseña, **Drawer + Bottom Navigation** |
| Comprador | Inicio, catálogo con filtros, detalle de producto, carrito (CRUD), dirección de entrega, pasarela de pagos, confirmación, pedidos y perfil |
| Vendedor | Panel con indicadores, CRUD de productos, formulario de producto, historial de pedidos recibidos y perfil de vendedor |
| Administrador | Dashboard, gestión de usuarios (listar, crear, editar, suspender, eliminar), gestión de productos (moderación) y reporte de ventas con gráfico |

## Cuentas de demostración

La contraseña de todas es `Patitas2026*`. En la pantalla de inicio de sesión hay chips que llenan el formulario.

| Rol | Correo |
|---|---|
| Comprador | `comprador@patitas.co` |
| Vendedor | `vendedor@patitas.co` |
| Administrador | `admin@patitas.co` |

Cupón de prueba en el carrito: `PELUDO20`.

## Tecnología

- Kotlin 2.0, Android Gradle Plugin 8.7, `minSdk 26`, `targetSdk 35`
- Vistas XML + ViewBinding, Material Components 1.12 (tema Material 3 personalizado)
- AndroidX Biometric para el ingreso con huella
- Un tema por rol: verde azulado (comprador), terracota (vendedor) y gris pizarra (administrador)

## Estructura

```
app/src/main/
├── java/co/patitas/tienda/
│   ├── data/          Modelos y repositorio en memoria
│   ├── util/          Formatos (COP), sesión, biometría y extensiones
│   └── ui/
│       ├── auth/      Splash, bienvenida, login, registro, recuperación
│       ├── common/    Contenedor por rol (Drawer + Bottom Nav), perfil, gráfico de barras
│       ├── buyer/     Pantallas del comprador
│       ├── seller/    Pantallas del vendedor
│       └── admin/     Pantallas del administrador
└── res/
    ├── layout/        39 layouts XML (activities, fragments, ítems, diálogos)
    ├── drawable/      Íconos vectoriales, shapes, gradientes, layer-list y selectores
    ├── values/        colors, themes, styles, dimens, strings, arrays, attrs
    ├── color/         Listas de estados de color (selector)
    ├── menu/          Menús de Bottom Navigation, Drawer y acciones
    └── anim/          Animaciones del splash
```

## Compilar el APK

1. Abrir la carpeta en Android Studio y esperar la sincronización de Gradle.
2. **Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)**.
3. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

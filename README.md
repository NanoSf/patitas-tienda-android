# Patitas · Tienda virtual Android para mascotas

Aplicación nativa Android (Kotlin + vistas XML con Material Components) de un *marketplace* de productos para mascotas: alimento, snacks, rascadores, camas, ropa, juguetes e higiene.
Proyecto académico de **Desarrollo de Aplicaciones Móviles Nativas** — Fundación Universitaria Compensar (UCompensar).
Autor: **Einer Fabian Aponte Cubides**.

## Versión 2.0.0 · Entrega final (aplicativo funcional)

| Componente | Implementación |
|---|---|
| Autenticación | Firebase Authentication (correo y contraseña): registro, inicio y cierre de sesión y recuperación de contraseña para los tres roles. El rol vive en `users/{uid}` de Firestore. |
| Huella | `BiometricPrompt` + `CryptoObject`: la contraseña se cifra con una llave AES-256 del **Android Keystore** que solo se desbloquea con la huella (`CredentialVault`). |
| CRUD de usuarios | Administrador: crear (cuenta + perfil, sin cerrar su sesión), consultar en tiempo real, editar rol y estado, verificar vendedores, suspender con motivo y eliminar. |
| CRUD de productos | Vendedor: crear, editar, pausar, duplicar y eliminar, con foto desde la **cámara** (`TakePicture` + `FileProvider`) o la **galería** (Photo Picker). Administrador: aprobar, rechazar con motivo, despublicar y eliminar. |
| Carrito y órdenes | Carrito en `carts/{uid}/items` (agregar, cambiar cantidad, eliminar con deshacer, cupón). Al pagar se crea un pedido por tienda en `orders` con su **historial de estados**, se descuenta el stock y se vacía el carrito en un solo lote atómico. |
| Geolocalización | Fused Location Provider (Google Play Services) + `Geocoder` para llenar la dirección de entrega; las coordenadas se guardan en el pedido y se abren en Google Maps. |
| Pasarela de pagos | **ePayco** Smart Checkout en modo pruebas: sesión de pago por API, checkout en `WebView` con `checkout-v2.js` y validación de la transacción por su referencia (`ref_payco`). |
| Reportes | Ventas por semana, mes y año, por categoría y por tienda, calculadas con los pedidos reales; exportación CSV. |

### Fotos de productos sin Cloud Storage

El proyecto usa el **plan gratuito Spark** de Firebase, que no incluye Cloud Storage (exige el plan Blaze con tarjeta). Las fotos se reducen y comprimen en el teléfono (`ImageCodec`) y se guardan en Firestore como JPEG en Base64:

- **Miniatura** (320 px, ~40 KB) dentro del documento del producto, para las listas.
- **Foto completa** (1024 px, ~450 KB) en `productImages/{id}`, que solo se lee en el detalle.

## Cuentas de demostración

La contraseña de todas es `Patitas2026*`. En la pantalla de inicio de sesión hay chips que llenan el formulario. La primera vez que se ingresa con una de ellas, la app la crea en Firebase (y carga productos de ejemplo en la tienda demo).

| Rol | Correo |
|---|---|
| Comprador | `comprador@patitas.co` |
| Vendedor | `vendedor@patitas.co` |
| Administrador | `admin@patitas.co` |

Cupón de prueba en el carrito: `PELUDO20`.

## Configuración

1. **Firebase**: el archivo `app/google-services.json` conecta la app con el proyecto `patitas-tienda`. Para usar otro proyecto, reemplázalo y activa *Authentication › Correo/contraseña* y *Firestore Database*.
2. **Reglas de seguridad**: copia [`firestore.rules`](firestore.rules) en *Firebase Console › Firestore Database › Reglas* y publica.
3. **ePayco** (opcional para compilar, necesario para pagar): agrega las llaves de pruebas en `local.properties`:
   ```properties
   EPAYCO_PUBLIC_KEY=tu_llave_publica
   EPAYCO_PRIVATE_KEY=tu_llave_privada
   ```
   En GitHub Actions se leen de los secretos del repositorio con el mismo nombre. Sin llaves la app compila y funciona; solo el botón de pago avisa que la pasarela no está configurada.

> En producción, la creación de la sesión de ePayco y la eliminación de cuentas de Authentication se harían en un servidor (Cloud Functions), para no incluir la llave privada en la app. Este proyecto usa llaves de **pruebas**, que no mueven dinero, porque Cloud Functions también exige el plan Blaze.

## Tecnología

- Kotlin 2.0, Android Gradle Plugin 8.7, `minSdk 26`, `targetSdk 35`
- Vistas XML + ViewBinding, Material Components 1.12 (tema Material 3 personalizado, uno por rol)
- Firebase BoM 33.7: Authentication y Cloud Firestore (listeners en tiempo real con `LiveData`)
- Corrutinas de Kotlin (`lifecycleScope`, `await()` sobre las `Task` de Firebase)
- AndroidX Biometric + Android Keystore
- Google Play Services Location 21.3
- ePayco Smart Checkout (`apify.epayco.co` + `checkout-v2.js`)

## Estructura

```
app/src/main/java/co/patitas/tienda/
├── data/        Modelos, repositorios de Firestore (Auth, User, Product, Cart, Order),
│                reportes, cuentas de demostración y cliente de ePayco
├── util/        Formatos, huella (BiometricHelper, CredentialVault), imágenes (ImageCodec),
│                errores y extensiones de vistas
└── ui/
    ├── auth/    Splash, bienvenida, inicio de sesión, registro y recuperación
    ├── common/  Contenedor por rol (Drawer + Bottom Nav), perfil, pedidos y gráfico
    ├── buyer/   Inicio, catálogo, detalle, carrito, dirección (GPS), pago (ePayco) y pedidos
    ├── seller/  Panel, CRUD de productos con foto y pedidos recibidos
    └── admin/   Dashboard, CRUD de usuarios, moderación de productos y reportes
firestore.rules  Reglas de seguridad por rol y propietario
```

### Modelo de datos (Cloud Firestore)

| Colección | Contenido |
|---|---|
| `users/{uid}` | nombre, correo, teléfono, rol, estado, documento, tienda |
| `products/{id}` | datos del producto, estado de moderación, vendedor, miniatura |
| `productImages/{id}` | foto completa en Base64 |
| `carts/{uid}/items/{id}` | ítems del carrito |
| `orders/{id}` | comprador, tienda, ítems, totales, dirección con coordenadas, pago de ePayco e historial de estados |

## Compilar el APK

1. Abrir la carpeta en Android Studio y esperar la sincronización de Gradle.
2. **Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)**.
3. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

También se compila automáticamente con GitHub Actions en cada *push*; el APK de `main` se publica en **Releases**.

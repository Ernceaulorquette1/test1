# Especificación de Requisitos de Software (SRS) — NutriScan AI
*Basado en el estándar IEEE 830-1998 · Versión 1.0*

## 1. Introducción

### 1.1 Propósito
Este documento especifica los requisitos funcionales y no funcionales de **NutriScan AI**, aplicación móvil de nutrición que calcula calorías y macronutrientes a partir de fotografías de alimentos mediante inteligencia artificial.

### 1.2 Alcance
NutriScan AI permite a los usuarios: registrar comidas fotografiándolas, consultar información nutricional por código de barras, llevar historial y estadísticas de alimentación y peso, registrar consumo de agua, conversar con un asistente nutricional con IA y contratar un plan Premium por suscripción. Se distribuye por Google Play y debe escalar a más de 1 millón de usuarios.

### 1.3 Definiciones y acrónimos
| Término | Definición |
|---|---|
| IA | Inteligencia Artificial |
| Macro | Macronutriente (proteínas, carbohidratos, grasas) |
| JWT | JSON Web Token |
| TDEE | Gasto energético diario total |
| IMC | Índice de Masa Corporal |
| FCM | Firebase Cloud Messaging |

### 1.4 Referencias
- IEEE Std 830-1998 · Material Design 3 · OpenAPI 3.0 · Políticas de Google Play

## 2. Descripción general

### 2.1 Perspectiva del producto
Sistema cliente-servidor: app Flutter (Android/iOS) + API REST Spring Boot en Google Cloud (GKE), PostgreSQL, Redis, Firebase (Auth/Storage/FCM) y proveedores de visión IA intercambiables (OpenAI/Gemini).

### 2.2 Funciones del producto
Autenticación, perfil y objetivos, cámara IA, historial, estadísticas, escáner de códigos, chat IA, registro de agua y suscripciones Premium.

### 2.3 Características de los usuarios
Público general interesado en salud (18–65 años), sin conocimientos técnicos. Requiere una interfaz simple, en español, accesible.

### 2.4 Restricciones
- Cumplimiento de políticas de Google Play (suscripciones vía Play Billing).
- Protección de datos personales y de salud (consentimiento explícito, derecho de eliminación).
- El análisis IA es una **estimación**: se comunica al usuario y no constituye consejo médico.

## 3. Requisitos específicos

### 3.1 Requisitos funcionales

| ID | Requisito | Prioridad |
|---|---|---|
| RF-01 | Registro e inicio de sesión con email/contraseña | Alta |
| RF-02 | Inicio de sesión con Google y Apple | Alta |
| RF-03 | Recuperación de contraseña por correo | Alta |
| RF-04 | Captura y edición de perfil: edad, sexo, peso, altura, actividad, objetivo, peso objetivo | Alta |
| RF-05 | Cálculo automático de calorías objetivo (Mifflin-St Jeor) | Alta |
| RF-06 | Fotografiar comida o elegir imagen de galería | Alta |
| RF-07 | Detección de alimentos e ingredientes con estimación de porciones | Alta |
| RF-08 | Cálculo de calorías, proteínas, grasas, carbohidratos, fibra, sodio y azúcar | Alta |
| RF-09 | Guardado de comidas por tipo (desayuno, almuerzo, cena, snack) | Alta |
| RF-10 | Historial diario navegable con totales | Alta |
| RF-11 | Estadísticas de peso, IMC, calorías y macros con gráficos semanales/mensuales/anuales | Alta |
| RF-12 | Escáner de código de barras con información nutricional | Media |
| RF-13 | Chat nutricional con IA contextualizado al perfil | Alta |
| RF-14 | Registro de consumo de agua con meta diaria y recordatorios | Media |
| RF-15 | Suscripción Premium mensual y anual vía Google Play Billing | Alta |
| RF-16 | Límite de análisis IA diarios para usuarios gratuitos; ilimitado en Premium | Alta |
| RF-17 | Notificaciones push (recordatorios) vía FCM | Media |

### 3.2 Requisitos no funcionales

| ID | Categoría | Requisito |
|---|---|---|
| RNF-01 | Rendimiento | Respuesta de API < 500 ms p95 (excepto análisis IA < 15 s) |
| RNF-02 | Escalabilidad | 1M+ usuarios; API stateless con autoescalado horizontal (HPA 3–30 réplicas) |
| RNF-03 | Disponibilidad | 99,9% mensual; despliegues sin downtime |
| RNF-04 | Seguridad | JWT, HTTPS, protección SQLi/XSS/CSRF, cifrado de datos sensibles |
| RNF-05 | Usabilidad | Material Design 3, tema claro/oscuro, español |
| RNF-06 | Mantenibilidad | Clean Architecture, SOLID, cobertura de pruebas en módulos críticos |
| RNF-07 | Portabilidad | Android 8.0+ (API 26); código preparado para iOS |
| RNF-08 | Privacidad | Consentimiento, eliminación de cuenta y datos bajo demanda |

### 3.3 Interfaces externas
- **OpenAI Vision / Gemini Vision**: análisis de imágenes (HTTPS/JSON).
- **Open Food Facts**: datos por código de barras.
- **Firebase**: Auth (ID tokens), Storage (fotos), FCM (push).
- **Google Play Billing**: compras y verificación de suscripciones.

# DriverLink — reglas R8.
# Los modelos de dominio se mapean manualmente desde Firestore (sin reflexión),
# por lo que no requieren reglas -keep especiales.

# Mantener números de línea para reportes de Crashlytics legibles.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

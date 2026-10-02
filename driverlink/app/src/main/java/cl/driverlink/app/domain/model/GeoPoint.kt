package cl.driverlink.app.domain.model

/** Coordenada geográfica independiente del proveedor de mapas o de Firebase. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "Latitud fuera de rango" }
        require(longitude in -180.0..180.0) { "Longitud fuera de rango" }
    }
}

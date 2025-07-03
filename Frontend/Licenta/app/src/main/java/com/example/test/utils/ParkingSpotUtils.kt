package com.example.test.utils

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import java.text.SimpleDateFormat
import java.util.*

object ParkingSpotUtils {

    // --- CONSTANTE CONFIGURABILE ---
    private const val SPOT_WIDTH_METERS = 2.5
    private const val SPOT_LENGTH_METERS = 5.0
    private const val SPACING_METERS = 2.7

    // --- FUNCȚIE CENTRALIZATĂ PENTRU PARSAREA DATELOR ISO 8601 ---
    /**
     * Parsează o dată în format ISO 8601 în UTC timezone.
     * Suportă formate cu și fără milisecunde.
     * Returnează null dacă parsarea eșuează.
     */
    fun parseISO8601ToDate(iso8601String: String): Date? {
        return try {
            // Încercăm mai întâi formatul cu milisecunde
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(iso8601String)
        } catch (e: Exception) {
            try {
                // Fallback la formatul fără milisecunde
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(iso8601String)
            } catch (e2: Exception) {
                null // Returnăm null în loc de Date() pentru a evita confuzia
            }
        }
    }

    /**
     * Formatează o dată în format ISO 8601 UTC pentru trimiterea către backend.
     * Folosește formatul fără milisecunde pentru consistență.
     */
    fun formatDateToISO8601(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(date)
    }

    // --- AICI DEFINIM MANUAL ZONELE ȘI ORIENTAREA STRĂZILOR ---
    private val PREDEFINED_ZONES = listOf(
        ParkingZone(
            // Centrul aproximativ al zonei de parcare din campus
            center = LatLng(47.15688, 27.60408),
            // O rază suficient de mare pentru a acoperi locurile
            radius = 100.0, // în metri
            // Unghiul STRĂZII, măsurat.
            // Aleea Vasile Petrescu are o orientare de aprox. 140 de grade.
            roadHeading = 118.5 // în grade
        )
        // Poți adăuga alte zone aici...
    )

    /**
     * Generează o listă de poligoane aliniate.
     */
    fun generateAlignedParkingSpots(spots: List<com.example.test.models.ParkingSpot>): List<AlignedPolygon> {
        if (spots.isEmpty()) return emptyList()

        val firstSpotCenter = spots[0].toLatLng()

        // 1. DETERMINĂ ORIENTAREA RÂNDULUI (HEADING)
        val zone = PREDEFINED_ZONES.find { it.contains(firstSpotCenter) }
        val rowHeading: Double

        if (zone != null) {
            // A. Metoda preferată: Folosim unghiul exact, predefinit pentru stradă
            rowHeading = zone.roadHeading
        } else {
            // B. Metoda de rezervă (fallback): Calculăm unghiul din primele 2 puncte
            if (spots.size < 2) return emptyList()
            val secondSpotCenter = spots[1].toLatLng()
            rowHeading = SphericalUtil.computeHeading(firstSpotCenter, secondSpotCenter)
        }

        // 2. CALCULEAZĂ ORIENTAREA FIECĂRUI LOC (PERPENDICULAR PE STRADĂ)
        val spotOrientation = (rowHeading - 90.0 + 360.0) % 360.0

        val alignedPolygons = mutableListOf<AlignedPolygon>()
        var currentPosition = firstSpotCenter

        // 3. GENEREAZĂ POLIGOANELE
        spots.forEachIndexed { index, spot ->
            if (index > 0) {
                currentPosition = SphericalUtil.computeOffset(currentPosition, SPACING_METERS, rowHeading)
            }

            val points = calculateRotatedPolygonPoints(currentPosition, spotOrientation)
            alignedPolygons.add(AlignedPolygon(spot.id.toLong(), points))
        }

        return alignedPolygons
    }

    /**
     * Calculează cele 4 colțuri ale unui dreptunghi rotit.
     */
    private fun calculateRotatedPolygonPoints(center: LatLng, orientation: Double): List<LatLng> {
        val halfLen = SPOT_LENGTH_METERS / 2.0
        val halfWid = SPOT_WIDTH_METERS / 2.0

        val points = listOf(
            SphericalUtil.computeOffset(center, halfLen, orientation + 180),
            SphericalUtil.computeOffset(center, halfLen, orientation)
        ).flatMap { lineEndPoint ->
            listOf(
                SphericalUtil.computeOffset(lineEndPoint, halfWid, orientation - 90),
                SphericalUtil.computeOffset(lineEndPoint, halfWid, orientation + 90)
            )
        }
        return listOf(points[2], points[3], points[1], points[0])
    }
}

/**
 * Clasă pentru a defini o zonă cu orientare fixă.
 */
data class ParkingZone(
    val center: LatLng,
    val radius: Double, // în metri
    val roadHeading: Double // în grade (0=Nord, 90=Est, 180=Sud, 270=Vest)
) {
    fun contains(location: LatLng): Boolean {
        return SphericalUtil.computeDistanceBetween(center, location) <= radius
    }
}

/**
 * Clasă de date pentru a returna rezultatele.
 */
data class AlignedPolygon(
    val spotId: Long,
    val points: List<LatLng>
)
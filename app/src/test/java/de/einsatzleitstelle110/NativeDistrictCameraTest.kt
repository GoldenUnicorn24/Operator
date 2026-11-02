package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

class NativeDistrictCameraTest {
    @Test fun districtFitsAtMinimumZoomAndCannotBePannedAway() {
        assertEquals(0f, DistrictCamera.pan(900f, 320.0, 320.0), 0f)
        assertEquals(0f, DistrictCamera.pan(-900f, 220.0, 600.0), 0f)
    }
    @Test fun zoomedCameraStopsAtBothEdgesAndKeepsInteriorMovement() {
        assertEquals(320f, DistrictCamera.pan(900f, 960.0, 320.0), 0f)
        assertEquals(-320f, DistrictCamera.pan(-900f, 960.0, 320.0), 0f)
        assertEquals(80f, DistrictCamera.pan(80f, 960.0, 320.0), 0f)
    }
}

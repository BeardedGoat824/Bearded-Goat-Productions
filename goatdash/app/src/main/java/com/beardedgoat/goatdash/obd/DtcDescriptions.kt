package com.beardedgoat.goatdash.obd

/**
 * Plain-English meanings for the generic (SAE-defined) trouble codes people hit most often.
 * Manufacturer-specific codes (P1xxx, P3xxx, U1xxx...) vary by brand, so for anything not
 * listed we fall back to describing the system the code belongs to.
 */
object DtcDescriptions {

    private val known: Map<String, String> = mapOf(
        // Fuel and air metering
        "P0010" to "Intake camshaft timing actuator circuit (bank 1)",
        "P0011" to "Intake camshaft timing over-advanced (bank 1)",
        "P0012" to "Intake camshaft timing over-retarded (bank 1)",
        "P0013" to "Exhaust camshaft timing actuator circuit (bank 1)",
        "P0014" to "Exhaust camshaft timing over-advanced (bank 1)",
        "P0016" to "Crankshaft/camshaft position correlation (bank 1 sensor A)",
        "P0017" to "Crankshaft/camshaft position correlation (bank 1 sensor B)",
        "P0030" to "O2 sensor heater control circuit (bank 1 sensor 1)",
        "P0036" to "O2 sensor heater control circuit (bank 1 sensor 2)",
        "P0068" to "Manifold pressure / throttle position correlation",
        "P0087" to "Fuel rail pressure too low",
        "P0088" to "Fuel rail pressure too high",
        "P0100" to "Mass air flow (MAF) sensor circuit",
        "P0101" to "Mass air flow (MAF) sensor range/performance",
        "P0102" to "Mass air flow (MAF) sensor circuit low",
        "P0103" to "Mass air flow (MAF) sensor circuit high",
        "P0106" to "Manifold pressure (MAP) sensor range/performance",
        "P0107" to "Manifold pressure (MAP) sensor circuit low",
        "P0108" to "Manifold pressure (MAP) sensor circuit high",
        "P0110" to "Intake air temperature sensor circuit",
        "P0112" to "Intake air temperature sensor circuit low",
        "P0113" to "Intake air temperature sensor circuit high",
        "P0115" to "Engine coolant temperature sensor circuit",
        "P0116" to "Engine coolant temperature sensor range/performance",
        "P0117" to "Engine coolant temperature sensor circuit low",
        "P0118" to "Engine coolant temperature sensor circuit high",
        "P0120" to "Throttle position sensor circuit",
        "P0121" to "Throttle position sensor range/performance",
        "P0122" to "Throttle position sensor circuit low",
        "P0123" to "Throttle position sensor circuit high",
        "P0125" to "Coolant temperature too low for closed-loop fuel control",
        "P0128" to "Coolant thermostat (engine running colder than expected)",
        "P0130" to "O2 sensor circuit (bank 1 sensor 1)",
        "P0131" to "O2 sensor circuit low voltage (bank 1 sensor 1)",
        "P0132" to "O2 sensor circuit high voltage (bank 1 sensor 1)",
        "P0133" to "O2 sensor slow response (bank 1 sensor 1)",
        "P0134" to "O2 sensor no activity detected (bank 1 sensor 1)",
        "P0135" to "O2 sensor heater circuit (bank 1 sensor 1)",
        "P0136" to "O2 sensor circuit (bank 1 sensor 2)",
        "P0137" to "O2 sensor circuit low voltage (bank 1 sensor 2)",
        "P0138" to "O2 sensor circuit high voltage (bank 1 sensor 2)",
        "P0140" to "O2 sensor no activity detected (bank 1 sensor 2)",
        "P0141" to "O2 sensor heater circuit (bank 1 sensor 2)",
        "P0151" to "O2 sensor circuit low voltage (bank 2 sensor 1)",
        "P0153" to "O2 sensor slow response (bank 2 sensor 1)",
        "P0155" to "O2 sensor heater circuit (bank 2 sensor 1)",
        "P0161" to "O2 sensor heater circuit (bank 2 sensor 2)",
        "P0171" to "System too lean (bank 1)",
        "P0172" to "System too rich (bank 1)",
        "P0174" to "System too lean (bank 2)",
        "P0175" to "System too rich (bank 2)",
        "P0191" to "Fuel rail pressure sensor range/performance",
        "P0200" to "Fuel injector circuit",
        "P0201" to "Fuel injector circuit — cylinder 1",
        "P0202" to "Fuel injector circuit — cylinder 2",
        "P0203" to "Fuel injector circuit — cylinder 3",
        "P0204" to "Fuel injector circuit — cylinder 4",
        "P0217" to "Engine overheating condition",
        "P0219" to "Engine overspeed condition",
        "P0220" to "Throttle position sensor B circuit",
        "P0234" to "Turbo/supercharger overboost",
        "P0299" to "Turbo/supercharger underboost",
        // Ignition and misfire
        "P0300" to "Random/multiple cylinder misfire detected",
        "P0301" to "Cylinder 1 misfire detected",
        "P0302" to "Cylinder 2 misfire detected",
        "P0303" to "Cylinder 3 misfire detected",
        "P0304" to "Cylinder 4 misfire detected",
        "P0305" to "Cylinder 5 misfire detected",
        "P0306" to "Cylinder 6 misfire detected",
        "P0307" to "Cylinder 7 misfire detected",
        "P0308" to "Cylinder 8 misfire detected",
        "P0316" to "Misfire detected on startup (first 1000 revolutions)",
        "P0325" to "Knock sensor 1 circuit (bank 1)",
        "P0327" to "Knock sensor 1 circuit low (bank 1)",
        "P0328" to "Knock sensor 1 circuit high (bank 1)",
        "P0332" to "Knock sensor 2 circuit low (bank 2)",
        "P0335" to "Crankshaft position sensor A circuit",
        "P0336" to "Crankshaft position sensor A range/performance",
        "P0340" to "Camshaft position sensor A circuit (bank 1)",
        "P0341" to "Camshaft position sensor A range/performance (bank 1)",
        "P0345" to "Camshaft position sensor A circuit (bank 2)",
        "P0351" to "Ignition coil A primary/secondary circuit",
        "P0352" to "Ignition coil B primary/secondary circuit",
        "P0353" to "Ignition coil C primary/secondary circuit",
        "P0354" to "Ignition coil D primary/secondary circuit",
        // Emissions controls
        "P0400" to "Exhaust gas recirculation (EGR) flow",
        "P0401" to "EGR flow insufficient",
        "P0402" to "EGR flow excessive",
        "P0403" to "EGR control circuit",
        "P0404" to "EGR control circuit range/performance",
        "P0405" to "EGR sensor A circuit low",
        "P0411" to "Secondary air injection incorrect flow",
        "P0420" to "Catalytic converter efficiency below threshold (bank 1)",
        "P0421" to "Warm-up catalyst efficiency below threshold (bank 1)",
        "P0430" to "Catalytic converter efficiency below threshold (bank 2)",
        "P0440" to "Evaporative emission (EVAP) system",
        "P0441" to "EVAP system incorrect purge flow",
        "P0442" to "EVAP system small leak detected",
        "P0443" to "EVAP purge control valve circuit",
        "P0446" to "EVAP vent control circuit",
        "P0449" to "EVAP vent valve/solenoid circuit",
        "P0455" to "EVAP system large leak detected (check the gas cap)",
        "P0456" to "EVAP system very small leak detected",
        "P0457" to "EVAP leak detected — fuel cap loose or off",
        "P0496" to "EVAP high purge flow",
        // Speed, idle and auxiliary inputs
        "P0500" to "Vehicle speed sensor",
        "P0501" to "Vehicle speed sensor range/performance",
        "P0505" to "Idle air control system",
        "P0506" to "Idle speed lower than expected",
        "P0507" to "Idle speed higher than expected",
        "P0520" to "Oil pressure sensor/switch circuit",
        "P0521" to "Oil pressure sensor/switch range/performance",
        "P0562" to "System voltage low",
        "P0563" to "System voltage high",
        "P0571" to "Brake switch A circuit",
        // Computer and outputs
        "P0600" to "Serial communication link",
        "P0601" to "Engine control module memory checksum error",
        "P0603" to "Engine control module keep-alive memory error",
        "P0606" to "Engine control module processor fault",
        "P0607" to "Control module performance",
        "P0627" to "Fuel pump control circuit open",
        "P0641" to "Sensor reference voltage A circuit open",
        "P0700" to "Transmission control system (check transmission codes)",
        "P0705" to "Transmission range sensor circuit",
        "P0715" to "Transmission input/turbine speed sensor circuit",
        "P0720" to "Transmission output speed sensor circuit",
        "P0730" to "Incorrect gear ratio",
        "P0740" to "Torque converter clutch circuit",
        "P0741" to "Torque converter clutch stuck off",
        "P0750" to "Shift solenoid A",
        "P0755" to "Shift solenoid B",
        // Network
        "U0001" to "High speed CAN communication bus",
        "U0073" to "Control module communication bus off",
        "U0100" to "Lost communication with engine control module (ECM/PCM)",
        "U0101" to "Lost communication with transmission control module (TCM)",
        "U0121" to "Lost communication with anti-lock brake (ABS) module",
        "U0140" to "Lost communication with body control module (BCM)",
        "U0155" to "Lost communication with instrument cluster",
    )

    private val systems = mapOf(
        "P00" to "Fuel/air metering or auxiliary emission controls",
        "P01" to "Fuel and air metering",
        "P02" to "Fuel and air metering (injector circuit)",
        "P03" to "Ignition system or misfire",
        "P04" to "Auxiliary emission controls",
        "P05" to "Vehicle speed, idle control or auxiliary inputs",
        "P06" to "Computer or auxiliary outputs",
        "P07" to "Transmission",
        "P08" to "Transmission",
        "P09" to "Transmission",
        "P0A" to "Hybrid propulsion system",
    )

    fun describe(code: String): String {
        known[code]?.let { return it }
        val manufacturer = code.length == 5 && (code[1] == '1' || code[1] == '3')
        return when {
            manufacturer -> "Manufacturer-specific ${systemName(code[0])} code — look it up for your make"
            code.startsWith("P") -> systems[code.take(3)] ?: "Powertrain (engine/transmission) fault"
            else -> "${systemName(code[0]).replaceFirstChar { it.uppercase() }} fault"
        }
    }

    private fun systemName(letter: Char) = when (letter) {
        'P' -> "powertrain"
        'C' -> "chassis (brakes, steering, suspension)"
        'B' -> "body (airbags, A/C, lighting)"
        'U' -> "network/communication"
        else -> "unknown"
    }
}

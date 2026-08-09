# TesMano Vehicle Appearance Model

Phase 1A keeps vehicle dimensions independent: `VehicleGeneration`, `VehicleTrim`, `WheelConfiguration`, `BrakeConfiguration`, `SpoilerConfiguration`, and `VehicleAppearanceOverride`.

Automatic detection is conservative. PN01, P74D, and Uberturbine21 do not establish Juniper generation. P74D establishes Performance trim only, and Performance trim never changes the selected wheels. A manual value is authoritative only for the dimension it supplies.

For an ambiguous Model Y, the image resolver keeps the legacy body fallback. The current PN01/P74D/Gemini fallback uses the bundled legacy dark-grey Gemini asset (`my_PMNG_WY19B.png`) until a native legacy PN01 compositor asset is added; it never chooses a Juniper body just to display Performance details.

`legacy_model_y_performance_dark_gemini` is the stable key for the bundled legacy Model Y Performance illustration at `car_images/custom/vehicle_custom_legacy_model_y_pn01_dark_gemini.png`. The shared Dashboard/widget resolver prefers it only for the legacy PN01 (or TeslaMate's PMNG proxy), Performance, dark-Gemini configuration. If the custom asset is unavailable, it returns `my_PMNG_WY19B.png`; custom artwork never causes a Juniper fallback.

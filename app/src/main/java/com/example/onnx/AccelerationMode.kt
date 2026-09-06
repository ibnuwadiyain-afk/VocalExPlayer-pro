package com.example.onnx

enum class AccelerationMode(val label: String, val description: String) {
    AUTO("Auto (Recommended)", "Chooses best available hardware engine"),
    CPU("CPU (Default)", "Highly compatible multi-threaded CPU inference"),
    NNAPI("NNAPI (Hardware NPU/GPU)", "Uses Android Neural Networks API if supported"),
    XNNPACK("XNNPACK", "Optimized ARM NEON acceleration for mobile CPUs");

    companion object {
        fun fromString(name: String?): AccelerationMode {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: AUTO
        }
    }
}

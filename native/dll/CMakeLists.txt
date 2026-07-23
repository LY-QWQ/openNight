set(JAR_SOURCE "${CMAKE_SOURCE_DIR}/opennight.jar")
set(JAR_STAGED "${CMAKE_CURRENT_BINARY_DIR}/opennight.jar")

if(NOT EXISTS ${JAR_SOURCE})
    message(WARNING
        "opennight.jar not found at ${JAR_SOURCE}; "
        "did you run the Gradle 'stageNativeJar' task first? "
        "The DLL will be built but the embedded jar resource will be empty.")
    file(WRITE ${JAR_STAGED} "")
else()
    configure_file(${JAR_SOURCE} ${JAR_STAGED} COPYONLY)
endif()

add_library(OpenNIGHT SHARED
    src/main.cpp
    src/jvm_attach.cpp
    src/jar_extract.cpp
    src/class_loader.cpp
    src/diagnostics.cpp
    res/opennight.rc
)

target_include_directories(OpenNIGHT PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/res
)

# rc.exe needs to find opennight.jar (staged into CMAKE_CURRENT_BINARY_DIR by the
# configure_file above) when assembling RCDATA from opennight.rc.
set_source_files_properties(res/opennight.rc PROPERTIES
    COMPILE_FLAGS "/I\"${CMAKE_CURRENT_BINARY_DIR}\""
)

target_compile_definitions(OpenNIGHT PRIVATE
    OPENZEN_DLL_EXPORTS
)

set_target_properties(OpenNIGHT PROPERTIES
    OUTPUT_NAME "OpenNIGHT"
    PREFIX ""
)

# We do not link jvm.dll: jvm.dll is already loaded in the host process when the
# DLL is injected, and JNI_GetCreatedJavaVMs is provided by it. We only need
# the JNI headers at compile time.

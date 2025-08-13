if(NOT TARGET oboe::oboe)
add_library(oboe::oboe SHARED IMPORTED)
set_target_properties(oboe::oboe PROPERTIES
    IMPORTED_LOCATION "/Users/bytedance/.gradle/caches/transforms-3/a220dbb80781b4a1f47c15588fd9baf1/transformed/oboe-1.8.0/prefab/modules/oboe/libs/android.x86_64/liboboe.so"
    INTERFACE_INCLUDE_DIRECTORIES "/Users/bytedance/.gradle/caches/transforms-3/a220dbb80781b4a1f47c15588fd9baf1/transformed/oboe-1.8.0/prefab/modules/oboe/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()


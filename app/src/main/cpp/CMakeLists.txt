cmake_minimum_required(VERSION 3.22.1)

project("secure-lib")

add_library(secure-lib SHARED secure_lib.cpp)

target_compile_features(secure-lib PRIVATE cxx_std_17)

target_compile_options(secure-lib PRIVATE
    -O2
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
)

target_link_options(secure-lib PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    -Wl,-z,relro
    -Wl,-z,now
)

target_link_libraries(secure-lib PRIVATE log)

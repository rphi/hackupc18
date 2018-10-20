cmake_minimum_required(VERSION 3.12 FATAL_ERROR)
cmake_policy(VERSION 3.12)
set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} "${CMAKE_SOURCE_DIR}/cmake")
set(CMAKE_CXX_STANDARD 17)

project(ObjDetectServer LANGUAGES C CXX CUDA)

find_package(Boost 1.68.0 REQUIRED system)
find_package(Threads REQUIRED)

set(ODS_SOURCES
    src/main.cpp
)

add_executable(objdetectserver ${ODS_SOURCES})
target_include_directories(objdetectserver PUBLIC ${PROJECT_SOURCE_DIR}/include)
target_include_directories(objdetectserver PRIVATE ${PROJECT_SOURCE_DIR}/darknet/include
  /usr/local/cuda/include)
target_link_libraries(objdetectserver PRIVATE
    Boost::boost
    Boost::system
    Threads::Threads
    ${PROJECT_SOURCE_DIR}/darknet/libdarknet.so)

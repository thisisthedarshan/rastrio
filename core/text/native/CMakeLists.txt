#[[
 * Copyright 2026 Darshan <darshan@alchiemy.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
#]]

cmake_minimum_required(VERSION 3.22)
project(rastrio_text LANGUAGES C CXX)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
if(POLICY CMP0135)
 cmake_policy(SET CMP0135 NEW)
endif()
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
include(FetchContent)
FetchContent_Declare(freetype URL "${CMAKE_CURRENT_SOURCE_DIR}/upstream/freetype-2.14.3.tar.xz"
 URL_HASH SHA256=36bc4f1cc413335368ee656c42afca65c5a3987e8768cc28cf11ba775e785a5f)
set(FT_DISABLE_ZLIB ON CACHE BOOL "" FORCE)
set(FT_DISABLE_BZIP2 ON CACHE BOOL "" FORCE)
set(FT_DISABLE_PNG ON CACHE BOOL "" FORCE)
set(FT_DISABLE_HARFBUZZ ON CACHE BOOL "" FORCE)
set(FT_DISABLE_BROTLI ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(freetype)
FetchContent_Declare(harfbuzz URL "${CMAKE_CURRENT_SOURCE_DIR}/upstream/harfbuzz-14.6.0.tar.xz"
 URL_HASH SHA256=d07a007327277708a2a73ae437887cdbaf282937f6d03ca5467723e9099af586)
set(HB_BUILD_SUBSET OFF CACHE BOOL "" FORCE)
set(HB_BUILD_RASTER OFF CACHE BOOL "" FORCE)
set(HB_BUILD_VECTOR OFF CACHE BOOL "" FORCE)
set(HB_BUILD_GPU OFF CACHE BOOL "" FORCE)
set(HB_HAVE_CORETEXT OFF CACHE BOOL "" FORCE)
set(HB_HAVE_FREETYPE ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(harfbuzz)
add_library(rastrio_text SHARED rastrio_text.cpp)
set_target_properties(rastrio_text PROPERTIES
 LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}"
 RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}"
 RUNTIME_OUTPUT_DIRECTORY_RELEASE "${CMAKE_CURRENT_BINARY_DIR}")
target_compile_features(rastrio_text PRIVATE cxx_std_17)
target_link_libraries(rastrio_text PRIVATE harfbuzz freetype)
if(NOT ANDROID)
 if(RASTRIO_JNI_INCLUDE)
  file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/jni-header")
  configure_file("${RASTRIO_JNI_INCLUDE}/jni.h" "${CMAKE_CURRENT_BINARY_DIR}/jni-header/jni.h" COPYONLY)
  target_include_directories(rastrio_text PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/jni-header")
 else()
  find_package(JNI REQUIRED COMPONENTS JVM)
  target_include_directories(rastrio_text PRIVATE ${JNI_INCLUDE_DIRS})
 endif()
endif()

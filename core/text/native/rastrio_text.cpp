/**
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
 **/

#include <jni.h>
#include <ft2build.h>
#include FT_FREETYPE_H
#include <hb.h>
#include <hb-ft.h>
#include <vector>
#include <stdexcept>
#include <string>
#include <cmath>
#include <algorithm>

namespace {
void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), message);
}
struct Face {
    FT_Library library = nullptr;
    FT_Face face = nullptr;
    std::vector<unsigned char> bytes;
    Face(JNIEnv* env, jbyteArray data) {
        if (!data) throw std::runtime_error("Missing font");
        const auto size = env->GetArrayLength(data);
        if (size <= 0 || size > 1024 * 1024) throw std::runtime_error("Font resource limit");
        bytes.resize(size);
        env->GetByteArrayRegion(data, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
        if (env->ExceptionCheck()) throw std::runtime_error("Font copy failed");
        if (FT_Init_FreeType(&library)) throw std::runtime_error("FreeType initialization failed");
        if (FT_New_Memory_Face(library, bytes.data(), size, 0, &face)) {
            FT_Done_FreeType(library); library = nullptr;
            throw std::runtime_error("Invalid font resource");
        }
    }
    ~Face() { if (face) FT_Done_Face(face); if (library) FT_Done_FreeType(library); }
    void size(double mm, int dx, int dy) {
        if (!std::isfinite(mm) || mm <= 0 || mm > 50 || dx < 72 || dx > 2540 || dy < 72 || dy > 2540)
            throw std::runtime_error("Invalid text geometry");
        if (FT_Set_Char_Size(face, 0, std::llround(mm * 72.0 / 25.4 * 64), dx, dy))
            throw std::runtime_error("Font size unavailable");
    }
};
struct Shape {
    hb_font_t* font = nullptr;
    hb_buffer_t* buffer = nullptr;
    Shape(JNIEnv* env, Face& face, jstring text) {
        if (!text || env->GetStringLength(text) > 65536) throw std::runtime_error("Text resource limit");
        std::vector<jchar> chars(env->GetStringLength(text));
        env->GetStringRegion(text, 0, chars.size(), chars.data());
        if (env->ExceptionCheck()) throw std::runtime_error("Text copy failed");
        font = hb_ft_font_create_referenced(face.face);
        hb_ft_font_set_load_flags(font, FT_LOAD_NO_HINTING | FT_LOAD_NO_BITMAP);
        buffer = hb_buffer_create();
        hb_buffer_set_cluster_level(buffer, HB_BUFFER_CLUSTER_LEVEL_MONOTONE_GRAPHEMES);
        hb_buffer_add_utf16(buffer, reinterpret_cast<uint16_t*>(chars.data()), chars.size(), 0, chars.size());
        hb_buffer_set_language(buffer, hb_language_from_string("und", -1));
        hb_buffer_set_direction(buffer, HB_DIRECTION_LTR);
        hb_buffer_guess_segment_properties(buffer);
        hb_shape(font, buffer, nullptr, 0);
        if (!hb_buffer_allocation_successful(buffer) || hb_buffer_get_length(buffer) > 131072) {
            hb_buffer_destroy(buffer); buffer = nullptr; hb_font_destroy(font); font = nullptr;
            throw std::runtime_error("Shaping resource limit");
        }
    }
    ~Shape() { if (buffer) hb_buffer_destroy(buffer); if (font) hb_font_destroy(font); }
};
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_circuitnext_rastrio_core_text_NativeTextBridge_metrics(JNIEnv* env, jobject, jbyteArray data) {
    try {
        Face f(env, data);
        jlong values[] = { f.face->units_per_EM, f.face->ascender, -f.face->descender, f.face->height };
        auto result = env->NewLongArray(4);
        if (result) env->SetLongArrayRegion(result, 0, 4, values);
        return result;
    } catch (const std::exception& e) { if (!env->ExceptionCheck()) fail(env, e.what()); return nullptr; }
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_circuitnext_rastrio_core_text_NativeTextBridge_shape(JNIEnv* env, jobject, jbyteArray data,
 jstring text, jdouble mm, jint dx, jint dy) {
    try {
        Face f(env, data); f.size(mm, dx, dy); Shape s(env, f, text);
        unsigned int count = 0;
        auto info = hb_buffer_get_glyph_infos(s.buffer, &count);
        auto pos = hb_buffer_get_glyph_positions(s.buffer, &count);
        std::vector<jlong> values(count * 2);
        for (unsigned int i = 0; i < count; ++i) {
            if (info[i].codepoint == 0) throw std::runtime_error("Missing glyph at " + std::to_string(info[i].cluster));
            values[i * 2] = info[i].cluster;
            values[i * 2 + 1] = pos[i].x_advance;
        }
        auto result = env->NewLongArray(values.size());
        if (result) env->SetLongArrayRegion(result, 0, values.size(), values.data());
        return result;
    } catch (const std::exception& e) { if (!env->ExceptionCheck()) fail(env, e.what()); return nullptr; }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_circuitnext_rastrio_core_text_NativeTextBridge_raster(JNIEnv* env, jobject, jbyteArray data,
 jstring text, jdouble mm, jint dx, jint dy, jint width, jint height, jint baseline, jint origin, jobject cancelled) {
    try {
        if (width <= 0 || width > 32768 || height <= 0 || height > 32768 || baseline < 0 || baseline > height || origin < 0)
            throw std::runtime_error("Invalid raster box");
        const int row = (width + 7) / 8;
        if (static_cast<int64_t>(row) * height > 8 * 1024 * 1024) throw std::runtime_error("Raster resource limit");
        Face f(env, data); f.size(mm, dx, dy); Shape s(env, f, text);
        unsigned int count = 0;
        auto info = hb_buffer_get_glyph_infos(s.buffer, &count);
        auto pos = hb_buffer_get_glyph_positions(s.buffer, &count);
        std::vector<jbyte> bytes(row * height, 0);
        int64_t pen = static_cast<int64_t>(origin) * 64;
        if (!cancelled) throw std::runtime_error("Missing cancellation callback");
        auto callbackClass = env->GetObjectClass(cancelled);
        auto invoke = env->GetMethodID(callbackClass, "invoke", "()Ljava/lang/Object;");
        auto boolClass = env->FindClass("java/lang/Boolean");
        auto booleanValue = env->GetMethodID(boolClass, "booleanValue", "()Z");
        if (env->ExceptionCheck()) throw std::runtime_error("Invalid cancellation callback");
        for (unsigned int i = 0; i < count; ++i) {
            if (info[i].codepoint == 0) throw std::runtime_error("Missing glyph at " + std::to_string(info[i].cluster));
            if (i % 64 == 0) {
                auto flag = env->CallObjectMethod(cancelled, invoke);
                if (env->ExceptionCheck()) throw std::runtime_error("Cancellation callback failed");
                const bool stop = env->CallBooleanMethod(flag, booleanValue);
                env->DeleteLocalRef(flag);
                if (env->ExceptionCheck()) throw std::runtime_error("Cancellation callback failed");
                if (stop) throw std::runtime_error("Text rasterization cancelled");
            }
            if (FT_Load_Glyph(f.face, info[i].codepoint, FT_LOAD_NO_HINTING | FT_LOAD_NO_BITMAP))
                throw std::runtime_error("Glyph load failed");
            const int64_t glyphWidth = (f.face->glyph->metrics.width + 127) / 64;
            const int64_t glyphHeight = (f.face->glyph->metrics.height + 127) / 64;
            if (glyphWidth < 0 || glyphHeight < 0 || glyphWidth > 32768 || glyphHeight > 32768 ||
                glyphWidth * glyphHeight > 8 * 1024 * 1024) throw std::runtime_error("Glyph raster resource limit");
            if (FT_Render_Glyph(f.face->glyph, FT_RENDER_MODE_NORMAL)) throw std::runtime_error("Glyph rasterization failed");
            auto& bitmap = f.face->glyph->bitmap;
            if (bitmap.width && bitmap.rows && (bitmap.pixel_mode != FT_PIXEL_MODE_GRAY || bitmap.num_grays != 256 || bitmap.pitch < 0))
                throw std::runtime_error("Unsupported coverage format");
            const int64_t left = std::llround((pen + pos[i].x_offset) / 64.0) + f.face->glyph->bitmap_left;
            const int64_t top = baseline - std::llround(pos[i].y_offset / 64.0) - f.face->glyph->bitmap_top;
            for (unsigned int y = 0; y < bitmap.rows; ++y) for (unsigned int x = 0; x < bitmap.width; ++x) {
                if (bitmap.buffer[y * bitmap.pitch + x] < 128) continue;
                const int64_t px = left + x, py = top + y;
                if (px < 0 || px >= width || py < 0 || py >= height) throw std::runtime_error("Raster ink exceeds final box");
                bytes[py * row + px / 8] |= static_cast<jbyte>(0x80 >> (px % 8));
            }
            pen += pos[i].x_advance;
        }
        auto result = env->NewByteArray(bytes.size());
        if (result) env->SetByteArrayRegion(result, 0, bytes.size(), bytes.data());
        return result;
    } catch (const std::exception& e) { if (!env->ExceptionCheck()) fail(env, e.what()); return nullptr; }
}

#include "music/feature_matrix.hpp"
#include "music/ranker.hpp"

#include <jni.h>

#include <stdexcept>
#include <string>
#include <vector>

namespace {

std::string java_string(JNIEnv* environment, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* characters = environment->GetStringUTFChars(value, nullptr);
    if (characters == nullptr) {
        throw std::runtime_error("Unable to read Java string");
    }
    std::string result(characters);
    environment->ReleaseStringUTFChars(value, characters);
    return result;
}

std::vector<std::string> java_strings(JNIEnv* environment, jobjectArray values) {
    const jsize length = environment->GetArrayLength(values);
    std::vector<std::string> result;
    result.reserve(static_cast<std::size_t>(length));
    for (jsize index = 0; index < length; ++index) {
        auto value = static_cast<jstring>(environment->GetObjectArrayElement(values, index));
        result.push_back(java_string(environment, value));
        environment->DeleteLocalRef(value);
    }
    return result;
}

std::vector<double> java_doubles(JNIEnv* environment, jdoubleArray values) {
    const jsize length = environment->GetArrayLength(values);
    std::vector<double> result(static_cast<std::size_t>(length));
    environment->GetDoubleArrayRegion(values, 0, length, result.data());
    if (environment->ExceptionCheck()) {
        throw std::runtime_error("Unable to read Java double array");
    }
    return result;
}

std::vector<bool> java_booleans(JNIEnv* environment, jbooleanArray values) {
    const jsize length = environment->GetArrayLength(values);
    std::vector<jboolean> raw(static_cast<std::size_t>(length));
    environment->GetBooleanArrayRegion(values, 0, length, raw.data());
    if (environment->ExceptionCheck()) {
        throw std::runtime_error("Unable to read Java boolean array");
    }
    std::vector<bool> result;
    result.reserve(raw.size());
    for (const jboolean value : raw) {
        result.push_back(value == JNI_TRUE);
    }
    return result;
}

void validate_lengths(
    const std::size_t rows,
    const std::vector<std::string>& ids,
    const std::vector<std::string>& artists,
    const std::vector<std::string>& genres,
    const std::vector<bool>& exploration) {
    if (ids.size() != rows
        || artists.size() != rows
        || genres.size() != rows
        || exploration.size() != rows) {
        throw std::invalid_argument("Candidate metadata length does not match feature rows");
    }
}

jobjectArray to_java_strings(JNIEnv* environment, const std::vector<music::RankedItem>& ranked) {
    jclass string_class = environment->FindClass("java/lang/String");
    jobjectArray result = environment->NewObjectArray(
        static_cast<jsize>(ranked.size()),
        string_class,
        nullptr);
    for (std::size_t index = 0; index < ranked.size(); ++index) {
        jstring value = environment->NewStringUTF(ranked[index].track_id.c_str());
        environment->SetObjectArrayElement(result, static_cast<jsize>(index), value);
        environment->DeleteLocalRef(value);
    }
    environment->DeleteLocalRef(string_class);
    return result;
}

jdoubleArray to_java_doubles(
    JNIEnv* environment,
    const std::vector<music::RankedItem>& ranked,
    const double music::RankedItem::* member) {
    std::vector<double> values;
    values.reserve(ranked.size());
    for (const auto& item : ranked) {
        values.push_back(item.*member);
    }
    jdoubleArray result = environment->NewDoubleArray(static_cast<jsize>(values.size()));
    environment->SetDoubleArrayRegion(
        result,
        0,
        static_cast<jsize>(values.size()),
        values.data());
    return result;
}

void throw_java_exception(JNIEnv* environment, const std::exception& error) {
    jclass exception_class = environment->FindClass("java/lang/IllegalArgumentException");
    if (exception_class != nullptr) {
        environment->ThrowNew(exception_class, error.what());
        environment->DeleteLocalRef(exception_class);
    }
}

}

extern "C" JNIEXPORT jobject JNICALL
Java_com_orhanyc_music_ranking_NativeRanker_nativeRank(
    JNIEnv* environment,
    jobject,
    jdoubleArray matrix_values,
    jint rows,
    jint columns,
    jobjectArray track_ids,
    jobjectArray artists,
    jobjectArray genres,
    jbooleanArray exploration,
    jint limit,
    jdouble diversity_weight,
    jdouble exploration_weight) {
    try {
        if (rows <= 0 || columns <= 0 || limit <= 0) {
            throw std::invalid_argument("Ranking dimensions and limit must be positive");
        }
        const auto values = java_doubles(environment, matrix_values);
        const auto ids = java_strings(environment, track_ids);
        const auto artist_values = java_strings(environment, artists);
        const auto genre_values = java_strings(environment, genres);
        const auto exploration_values = java_booleans(environment, exploration);
        validate_lengths(
            static_cast<std::size_t>(rows),
            ids,
            artist_values,
            genre_values,
            exploration_values);

        std::vector<music::Candidate> candidates;
        candidates.reserve(static_cast<std::size_t>(rows));
        for (jint index = 0; index < rows; ++index) {
            const auto position = static_cast<std::size_t>(index);
            candidates.push_back(music::Candidate{
                ids[position],
                artist_values[position],
                genre_values[position],
                exploration_values[position],
            });
        }

        const music::FeatureMatrix matrix(
            values,
            static_cast<std::size_t>(rows),
            static_cast<std::size_t>(columns));
        const music::RankingOptions options{
            static_cast<std::size_t>(limit),
            music::clamp(diversity_weight, 0.0, 1.0),
            music::clamp(exploration_weight, 0.0, 0.5),
            1,
            3,
        };
        const music::HybridRanker ranker;
        const auto ranked = ranker.rank(matrix, candidates, options);

        jobjectArray result_ids = to_java_strings(environment, ranked);
        jdoubleArray relevance = to_java_doubles(environment, ranked, &music::RankedItem::relevance);
        jdoubleArray diversity = to_java_doubles(environment, ranked, &music::RankedItem::diversity);
        jdoubleArray scores = to_java_doubles(environment, ranked, &music::RankedItem::score);

        jclass result_class = environment->FindClass(
            "com/orhanyc/music/ranking/NativeRanker$NativeResult");
        if (result_class == nullptr) {
            throw std::runtime_error("Unable to locate NativeResult class");
        }
        jmethodID constructor = environment->GetMethodID(
            result_class,
            "<init>",
            "([Ljava/lang/String;[D[D[D)V");
        if (constructor == nullptr) {
            throw std::runtime_error("Unable to locate NativeResult constructor");
        }
        jobject result = environment->NewObject(
            result_class,
            constructor,
            result_ids,
            relevance,
            diversity,
            scores);

        environment->DeleteLocalRef(result_class);
        environment->DeleteLocalRef(result_ids);
        environment->DeleteLocalRef(relevance);
        environment->DeleteLocalRef(diversity);
        environment->DeleteLocalRef(scores);
        return result;
    } catch (const std::exception& error) {
        throw_java_exception(environment, error);
        return nullptr;
    }
}


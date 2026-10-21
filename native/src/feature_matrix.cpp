#include "music/feature_matrix.hpp"

#include <algorithm>
#include <cmath>
#include <numeric>

namespace music {

FeatureMatrix::FeatureMatrix(
    std::vector<double> values,
    const std::size_t rows,
    const std::size_t columns)
    : values_(std::move(values)), rows_(rows), columns_(columns) {
    if (rows_ == 0 || columns_ == 0) {
        throw std::invalid_argument("Feature matrix dimensions must be positive");
    }
    if (values_.size() != rows_ * columns_) {
        throw std::invalid_argument("Feature matrix size does not match its dimensions");
    }
}

std::size_t FeatureMatrix::rows() const noexcept {
    return rows_;
}

std::size_t FeatureMatrix::columns() const noexcept {
    return columns_;
}

double FeatureMatrix::at(const std::size_t row_index, const std::size_t column_index) const {
    if (row_index >= rows_ || column_index >= columns_) {
        throw std::out_of_range("Feature matrix index is out of range");
    }
    return values_[row_index * columns_ + column_index];
}

std::span<const double> FeatureMatrix::row(const std::size_t row_index) const {
    if (row_index >= rows_) {
        throw std::out_of_range("Feature matrix row is out of range");
    }
    return std::span<const double>(values_.data() + row_index * columns_, columns_);
}

std::vector<double> FeatureMatrix::column(const std::size_t column_index) const {
    if (column_index >= columns_) {
        throw std::out_of_range("Feature matrix column is out of range");
    }
    std::vector<double> result;
    result.reserve(rows_);
    for (std::size_t row_index = 0; row_index < rows_; ++row_index) {
        result.push_back(at(row_index, column_index));
    }
    return result;
}

FeatureMatrix FeatureMatrix::normalized() const {
    std::vector<double> normalized_values(values_.size(), 0.0);
    for (std::size_t column_index = 0; column_index < columns_; ++column_index) {
        const auto values = column(column_index);
        const auto [minimum, maximum] = std::minmax_element(values.begin(), values.end());
        const double range = *maximum - *minimum;
        for (std::size_t row_index = 0; row_index < rows_; ++row_index) {
            const double value = at(row_index, column_index);
            normalized_values[row_index * columns_ + column_index] =
                range > 0.0 ? (value - *minimum) / range : value;
        }
    }
    return FeatureMatrix(std::move(normalized_values), rows_, columns_);
}

std::vector<double> FeatureWeights::as_vector() const {
    return {
        genre,
        energy,
        popularity,
        language,
        affinity,
        freshness,
        completion,
        quality,
    };
}

FeatureWeights FeatureWeights::normalized() const {
    const auto values = as_vector();
    const double total = std::accumulate(values.begin(), values.end(), 0.0);
    if (total <= 0.0) {
        throw std::invalid_argument("Feature weights must have a positive sum");
    }
    return FeatureWeights{
        genre / total,
        energy / total,
        popularity / total,
        language / total,
        affinity / total,
        freshness / total,
        completion / total,
        quality / total,
    };
}

double dot(const std::span<const double> left, const std::span<const double> right) {
    if (left.size() != right.size()) {
        throw std::invalid_argument("Vectors must have equal length");
    }
    return std::inner_product(left.begin(), left.end(), right.begin(), 0.0);
}

double sigmoid(const double value) {
    if (value >= 0) {
        const double exponential = std::exp(-value);
        return 1.0 / (1.0 + exponential);
    }
    const double exponential = std::exp(value);
    return exponential / (1.0 + exponential);
}

double clamp(const double value, const double minimum, const double maximum) {
    return std::max(minimum, std::min(value, maximum));
}

}


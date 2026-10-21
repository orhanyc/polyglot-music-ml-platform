#pragma once

#include <cstddef>
#include <span>
#include <stdexcept>
#include <vector>

namespace music {

class FeatureMatrix {
public:
    FeatureMatrix(std::vector<double> values, std::size_t rows, std::size_t columns);

    [[nodiscard]] std::size_t rows() const noexcept;
    [[nodiscard]] std::size_t columns() const noexcept;
    [[nodiscard]] double at(std::size_t row, std::size_t column) const;
    [[nodiscard]] std::span<const double> row(std::size_t row) const;
    [[nodiscard]] std::vector<double> column(std::size_t column) const;
    [[nodiscard]] FeatureMatrix normalized() const;

private:
    std::vector<double> values_;
    std::size_t rows_;
    std::size_t columns_;
};

struct FeatureWeights {
    double genre{0.25};
    double energy{0.15};
    double popularity{0.10};
    double language{0.05};
    double affinity{0.25};
    double freshness{0.08};
    double completion{0.07};
    double quality{0.05};

    [[nodiscard]] std::vector<double> as_vector() const;
    [[nodiscard]] FeatureWeights normalized() const;
};

[[nodiscard]] double dot(std::span<const double> left, std::span<const double> right);
[[nodiscard]] double sigmoid(double value);
[[nodiscard]] double clamp(double value, double minimum, double maximum);

}


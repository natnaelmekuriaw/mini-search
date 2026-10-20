package io.github.natnaelmekuriaw.minisearch.search;

final class Bm25Scorer {
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    double score(int documentCount, int documentFrequency, int termFrequency,
                 int documentLength, double averageDocumentLength) {
        if (documentCount < 1 || documentFrequency < 1 || documentFrequency > documentCount
                || documentLength < 0 || termFrequency < 0 || termFrequency > documentLength
                || averageDocumentLength <= 0) {
            throw new IllegalArgumentException("Invalid BM25 statistics");
        }
        if (termFrequency == 0) {
            return 0;
        }

        double idf = Math.log1p((documentCount - documentFrequency + 0.5)
                / (documentFrequency + 0.5));
        double lengthFactor = 1 - B + B * documentLength / averageDocumentLength;
        return idf * (termFrequency * (K1 + 1))
                / (termFrequency + K1 * lengthFactor);
    }
}

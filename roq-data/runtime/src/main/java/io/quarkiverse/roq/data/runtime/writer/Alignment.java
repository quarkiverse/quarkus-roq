package io.quarkiverse.roq.data.runtime.writer;

import java.util.function.BiPredicate;
import java.util.function.ToIntBiFunction;
import java.util.function.ToLongBiFunction;

/**
 * Aligns the elements of an original list with an updated list, so that unchanged elements (and their comments) stay
 * attached to the right item when elements are inserted, removed or modified.
 * <p>
 * The common start and end of the lists are matched first. In what is left, original and updated elements are paired
 * maximizing their similarity (equal elements first), while keeping the order. Extra updated elements are new, extra
 * original elements are removed.
 */
final class Alignment {

    /**
     * Above this size (original x updated) for the changed part, elements are paired in order.
     */
    private static final long MAX_PAIRING_CELLS = 1_000_000L;

    /**
     * The score of equal elements, which always wins over any number of merely similar ones.
     */
    private static final long EQUAL = 1_000_000L;

    private Alignment() {
    }

    /**
     * @param equal whether original i and updated j are equal
     * @param similarity how much original i and updated j are alike, {@code <= 0} means they can't be paired
     * @return for each updated element, the index of the original element it corresponds to, or {@code -1} if new
     */
    static int[] align(int originalSize, int updatedSize, BiPredicate<Integer, Integer> equal,
            ToIntBiFunction<Integer, Integer> similarity) {
        final int[] result = new int[updatedSize];
        int start = 0;
        while (start < originalSize && start < updatedSize && equal.test(start, start)) {
            result[start] = start;
            start++;
        }
        int originalEnd = originalSize;
        int updatedEnd = updatedSize;
        while (originalEnd > start && updatedEnd > start && equal.test(originalEnd - 1, updatedEnd - 1)) {
            originalEnd--;
            updatedEnd--;
            result[updatedEnd] = originalEnd;
        }
        pair(result, start, originalEnd, start, updatedEnd,
                (i, j) -> equal.test(i, j) ? EQUAL : similarity.applyAsInt(i, j));
        return result;
    }

    /**
     * Order-preserving pairing maximizing the total similarity in the changed part.
     */
    private static void pair(int[] result, int originalStart, int originalEnd, int updatedStart, int updatedEnd,
            ToLongBiFunction<Integer, Integer> similarity) {
        final int n = originalEnd - originalStart;
        final int m = updatedEnd - updatedStart;
        if ((long) n * m > MAX_PAIRING_CELLS) {
            for (int j = 0; j < m; j++) {
                result[updatedStart + j] = j < n ? originalStart + j : -1;
            }
            return;
        }
        final long[][] score = new long[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                score[i][j] = similarity.applyAsLong(originalStart + i, updatedStart + j);
            }
        }
        final long[][] best = new long[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                long value = Math.max(best[i + 1][j], best[i][j + 1]);
                if (score[i][j] > 0) {
                    value = Math.max(value, score[i][j] + best[i + 1][j + 1]);
                }
                best[i][j] = value;
            }
        }
        for (int j = 0; j < m; j++) {
            result[updatedStart + j] = -1;
        }
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (score[i][j] > 0 && best[i][j] == score[i][j] + best[i + 1][j + 1]) {
                result[updatedStart + j] = originalStart + i;
                i++;
                j++;
            } else if (best[i][j] == best[i + 1][j]) {
                i++;
            } else {
                j++;
            }
        }
    }
}

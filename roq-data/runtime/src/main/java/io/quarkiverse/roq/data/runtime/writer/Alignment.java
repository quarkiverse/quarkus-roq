package io.quarkiverse.roq.data.runtime.writer;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToIntBiFunction;
import java.util.stream.IntStream;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;

/**
 * Aligns the elements of an original list with an updated list, so that unchanged elements (and their comments) stay
 * attached to the right item when elements are inserted, removed or modified.
 * <p>
 * Equal elements are matched with a Myers diff. In each changed block, original and updated elements are paired (as
 * modified elements) maximizing their similarity, while keeping the order. Extra updated elements are new, extra
 * original elements are removed.
 */
final class Alignment {

    /**
     * Above this size (original x updated) for a changed block, elements are paired in order.
     */
    private static final long MAX_PAIRING_CELLS = 1_000_000L;

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
        final List<AbstractDelta<Integer>> deltas = DiffUtils.diff(indices(originalSize), indices(updatedSize), equal)
                .getDeltas();
        int original = 0;
        int updated = 0;
        for (AbstractDelta<Integer> delta : deltas) {
            // equal elements before the delta
            for (; updated < delta.getTarget().getPosition(); updated++, original++) {
                result[updated] = original;
            }
            final int originalEnd = original + delta.getSource().size();
            final int updatedEnd = updated + delta.getTarget().size();
            if (delta.getType() == DeltaType.CHANGE) {
                pair(result, original, originalEnd, updated, updatedEnd, similarity);
            } else {
                // INSERT: new elements, DELETE: removed elements
                for (int j = updated; j < updatedEnd; j++) {
                    result[j] = -1;
                }
            }
            original = originalEnd;
            updated = updatedEnd;
        }
        for (; updated < updatedSize; updated++, original++) {
            result[updated] = original;
        }
        return result;
    }

    private static List<Integer> indices(int size) {
        return IntStream.range(0, size).boxed().toList();
    }

    /**
     * Order-preserving pairing maximizing the total similarity in a changed block.
     */
    private static void pair(int[] result, int originalStart, int originalEnd, int updatedStart, int updatedEnd,
            ToIntBiFunction<Integer, Integer> similarity) {
        final int n = originalEnd - originalStart;
        final int m = updatedEnd - updatedStart;
        if ((long) n * m > MAX_PAIRING_CELLS) {
            for (int j = 0; j < m; j++) {
                result[updatedStart + j] = j < n ? originalStart + j : -1;
            }
            return;
        }
        final int[][] score = new int[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                score[i][j] = similarity.applyAsInt(originalStart + i, updatedStart + j);
            }
        }
        final int[][] best = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                int value = Math.max(best[i + 1][j], best[i][j + 1]);
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

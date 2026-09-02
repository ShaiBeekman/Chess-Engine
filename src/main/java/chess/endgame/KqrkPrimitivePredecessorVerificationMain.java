package main.java.chess.endgame;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;


/**
 * Correctness gate for primitive reverse edges.
 *
 * For random legal child states:
 *   1. generate primitive predecessors
 *   2. regenerate every predecessor's legal successors
 *   3. require the original child to be present
 *
 * It also checks that predecessor ids are unique.
 */
public final class KqrkPrimitivePredecessorVerificationMain {

    private static final int DEFAULT_CHILDREN = 50_000;


    public static void main(String[] args) {

        int children =
                args.length > 0
                        ? Integer.parseInt(args[0])
                        : DEFAULT_CHILDREN;

        verify(children, true, 0x51524B11L);
        verify(children, false, 0x51524B12L);
    }


    private static void verify(
            int children,
            boolean strongIsWhite,
            long seed
    ) {
        Random random = new Random(seed);

        KqrkPrimitivePredecessorGenerator.Buffer predecessors =
                new KqrkPrimitivePredecessorGenerator.Buffer(64);

        KqrkPrimitiveMoveGenerator.Buffer successors =
                new KqrkPrimitiveMoveGenerator.Buffer(64);

        int checked = 0;
        long edges = 0;
        int failures = 0;
        int duplicateFailures = 0;

        while (checked < children) {
            int child =
                    random.nextInt(
                            KqrkPrimitiveState.STATE_COUNT
                    );

            if (!KqrkPrimitiveRules.isStructurallyLegal(
                    child,
                    strongIsWhite
            )) {
                continue;
            }

            int count =
                    KqrkPrimitivePredecessorGenerator
                            .generateLegalPredecessors(
                                    child,
                                    strongIsWhite,
                                    predecessors
                            );

            Set<Integer> unique =
                    new HashSet<>();

            for (int i = 0; i < count; i++) {
                int parent = predecessors.state(i);

                if (!unique.add(parent)) {
                    duplicateFailures++;

                    if (duplicateFailures <= 10) {
                        System.out.println(
                                "Duplicate predecessor "
                                        + parent
                                        + " for child "
                                        + child
                        );
                    }
                }

                int successorCount =
                        KqrkPrimitiveMoveGenerator
                                .generateLegalSuccessors(
                                        parent,
                                        strongIsWhite,
                                        successors
                                );

                boolean found = false;

                for (int j = 0;
                     j < successorCount;
                     j++) {
                    if (successors.boundaryType(j)
                            == KqrkPrimitiveMoveGenerator.BOUNDARY_NONE
                            && successors.state(j) == child) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    failures++;

                    if (failures <= 10) {
                        System.out.println(
                                "Reverse-edge failure: parent "
                                        + parent
                                        + " -> child "
                                        + child
                        );
                    }
                }

                edges++;
            }

            checked++;
        }

        System.out.println();
        System.out.println(
                "Primitive predecessor verification — strong "
                        + (strongIsWhite ? "WHITE" : "BLACK")
        );
        System.out.println(
                "Legal children checked: " + checked
        );
        System.out.println(
                "Predecessor edges checked: " + edges
        );
        System.out.println(
                "Missing-forward-edge failures: " + failures
        );
        System.out.println(
                "Duplicate predecessor failures: "
                        + duplicateFailures
        );

        if (failures != 0
                || duplicateFailures != 0) {
            throw new IllegalStateException(
                    "Primitive predecessor verification failed."
            );
        }

        System.out.println(
                "PRIMITIVE PREDECESSOR VERIFICATION PASSED"
        );
    }
}

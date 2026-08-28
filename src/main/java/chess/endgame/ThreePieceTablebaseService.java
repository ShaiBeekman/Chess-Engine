package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.util.HashMap;
import java.util.Map;


/**
 * Lazily builds and caches exact 3-piece KQK/KRK tablebases.
 *
 * The first request for one material/color class performs the retrograde
 * construction. Every later request reuses the completed in-memory table.
 */
public final class ThreePieceTablebaseService {

    private final Map<Key, ThreePieceTablebase>
            cache =
            new HashMap<>();


    public synchronized ThreePieceTablebase get(
            PieceType majorType,
            Color majorColor
    ) {

        Key key =
                new Key(
                        majorType,
                        majorColor
                );


        ThreePieceTablebase existing =
                cache.get(
                        key
                );


        if (existing != null) {

            return existing;
        }


        ThreePieceTablebase tablebase =
                new ThreePieceTablebase(
                        majorType,
                        majorColor
                );


        tablebase.build();


        cache.put(
                key,
                tablebase
        );


        return tablebase;
    }


    public synchronized boolean isBuilt(
            PieceType majorType,
            Color majorColor
    ) {

        ThreePieceTablebase tablebase =
                cache.get(
                        new Key(
                                majorType,
                                majorColor
                        )
                );


        return tablebase != null
                &&
                tablebase.isBuilt();
    }


    public synchronized void clear() {

        cache.clear();
    }


    private record Key(
            PieceType majorType,
            Color majorColor
    ) {
    }
}
